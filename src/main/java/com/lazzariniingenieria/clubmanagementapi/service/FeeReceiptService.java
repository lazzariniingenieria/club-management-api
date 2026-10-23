package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceipt;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptLine;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptPdfRenderer;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeReceiptService {

    // paid_at is stored as an instant, but the admin picks calendar days as seen at the club: a payment recorded at
    // 22:00 on the 15th must fall on the 15th, not on the 16th as it would in UTC.
    private static final ZoneId CLUB_TIME_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Locale SPANISH_ARGENTINA = Locale.of("es", "AR");

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final ClubRepository clubRepository;
    private final FeeReceiptPdfRenderer feeReceiptPdfRenderer;

    public Optional<byte[]> generateReceiptsPdf(Long clubId, LocalDate paidFrom, LocalDate paidTo) {
        validateDateRange(paidFrom, paidTo);
        List<Payment> payments = findPaymentsCollectedBetween(clubId, paidFrom, paidTo);

        if (payments.isEmpty()) {
            log.info("No payments collected between {} and {} in clubId={}, no fee receipts generated", paidFrom, paidTo, clubId);

            return Optional.empty();
        }

        Club club = findClubOrThrow(clubId);
        List<FeeReceipt> receipts = buildReceipts(clubId, payments);
        byte[] pdf = feeReceiptPdfRenderer.render(club.getName(), receipts);
        log.info("Generated {} fee receipt(s) from {} payment(s) collected between {} and {} in clubId={}", receipts.size(),
                payments.size(), paidFrom, paidTo, clubId);

        return Optional.of(pdf);
    }

    private List<Payment> findPaymentsCollectedBetween(Long clubId, LocalDate paidFrom, LocalDate paidTo) {
        Instant collectedFrom = startOfDayAtClub(paidFrom);
        Instant collectedUntil = startOfDayAtClub(paidTo.plusDays(1));

        return paymentRepository.findActiveByClubIdAndPaidAtInRange(clubId, collectedFrom, collectedUntil);
    }

    private Instant startOfDayAtClub(LocalDate day) {
        ZonedDateTime startOfDay = day.atStartOfDay(CLUB_TIME_ZONE);

        return startOfDay.toInstant();
    }

    // Fees are sorted alphabetically before grouping, so each receipt is inserted into the LinkedHashMap when its
    // alphabetically first member shows up: the receipts come out ordered by first member and then by period.
    private List<FeeReceipt> buildReceipts(Long clubId, List<Payment> payments) {
        List<CollectedFee> fees = toCollectedFees(clubId, payments);
        fees.sort(alphabeticalOrder());
        Map<ReceiptKey, List<CollectedFee>> feesByReceipt = new LinkedHashMap<>();

        for (CollectedFee fee : fees) {
            List<CollectedFee> receiptFees = feesByReceipt.computeIfAbsent(receiptKeyOf(fee), key -> new ArrayList<>());
            receiptFees.add(fee);
        }

        List<FeeReceipt> receipts = new ArrayList<>();

        for (List<CollectedFee> receiptFees : feesByReceipt.values()) {
            receipts.add(toReceipt(receiptFees));
        }

        return receipts;
    }

    private List<CollectedFee> toCollectedFees(Long clubId, List<Payment> payments) {
        Map<Long, Member> membersById = findMembersById(clubId, payments);
        List<CollectedFee> fees = new ArrayList<>();

        for (Payment payment : payments) {
            Member member = membersById.get(payment.getMemberId());
            fees.add(new CollectedFee(member, payment));
        }

        return fees;
    }

    private Map<Long, Member> findMembersById(Long clubId, List<Payment> payments) {
        Set<Long> memberIds = new HashSet<>();

        for (Payment payment : payments) {
            memberIds.add(payment.getMemberId());
        }

        Map<Long, Member> membersById = new HashMap<>();

        for (Member member : memberRepository.findByClubIdAndIdIn(clubId, memberIds)) {
            membersById.put(member.getId(), member);
        }

        return membersById;
    }

    private Comparator<CollectedFee> alphabeticalOrder() {
        Collator collator = Collator.getInstance(SPANISH_ARGENTINA);
        Comparator<CollectedFee> byLastName = Comparator.comparing(fee -> fee.member().getLastName(), collator);
        Comparator<CollectedFee> byFullName = byLastName.thenComparing(fee -> fee.member().getFirstName(), collator);
        Comparator<CollectedFee> byMember = byFullName.thenComparing(fee -> fee.member().getId());

        return byMember.thenComparing(fee -> fee.payment().getPeriodCovered());
    }

    private ReceiptKey receiptKeyOf(CollectedFee fee) {
        Long familyGroupId = fee.member().getFamilyGroupId();
        Long individualMemberId = familyGroupId == null ? fee.member().getId() : null;

        return new ReceiptKey(familyGroupId, individualMemberId, fee.payment().getPeriodCovered());
    }

    private FeeReceipt toReceipt(List<CollectedFee> receiptFees) {
        List<FeeReceiptLine> lines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CollectedFee fee : receiptFees) {
            BigDecimal amount = fee.payment().getAmount();
            lines.add(new FeeReceiptLine(displayName(fee.member()), amount));
            totalAmount = totalAmount.add(amount);
        }

        CollectedFee firstFee = receiptFees.getFirst();

        return new FeeReceipt(firstFee.payment().getPeriodCovered(), lines, totalAmount);
    }

    private String displayName(Member member) {
        return member.getLastName() + " " + member.getFirstName();
    }

    private void validateDateRange(LocalDate paidFrom, LocalDate paidTo) {
        if (paidFrom.isAfter(paidTo)) {
            throw new InvalidDateRangeException("paidFrom " + paidFrom + " must not be after paidTo " + paidTo);
        }
    }

    private Club findClubOrThrow(Long clubId) {
        return clubRepository
                .findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException(clubId));
    }

    private record CollectedFee(Member member, Payment payment) {
    }

    // A member without a family group gets a receipt of their own, keyed by their member id instead of a group id.
    private record ReceiptKey(Long familyGroupId, Long individualMemberId, LocalDate periodCovered) {
    }
}
