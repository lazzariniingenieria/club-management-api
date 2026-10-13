package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.VoidPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentAlreadyVoidedException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberLastPaidPeriod;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final int AMOUNT_SCALE = 2;
    private static final Sort MOST_RECENTLY_PAID_FIRST = Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("periodCovered"),
            Sort.Order.desc("id"));

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final PaymentMapper paymentMapper;

    @Transactional
    public List<PaymentResponse> recordPayment(AuthenticatedUser currentUser, RecordPaymentRequest request) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, request.memberId());
        Long paidByMemberId = resolvePaidByMemberId(clubId, member, request.paidByMemberId());
        validateDistinctPeriods(request.periodsCovered());

        List<Payment> payments = buildPayments(request, paidByMemberId, currentUser.userAccountId());
        List<Payment> savedPayments = paymentRepository.saveAll(payments);
        log.info("Recorded {} payment(s) for memberId={} in clubId={}", savedPayments.size(), request.memberId(), clubId);

        return paymentMapper.toResponseList(savedPayments);
    }

    public List<PaymentResponse> listPaymentsForMember(Long clubId, Long memberId, boolean includeVoided) {
        findMemberOrThrow(clubId, memberId);
        List<Payment> payments = includeVoided
                ? paymentRepository.findByMemberIdOrderByPeriodCoveredDesc(memberId)
                : paymentRepository.findByMemberIdAndVoidedAtIsNullOrderByPeriodCoveredDesc(memberId);

        return paymentMapper.toResponseList(payments);
    }

    public List<PaymentResponse> searchPayments(Long clubId, PaymentSearchRequest filter) {
        validateDateRange(filter.from(), filter.to());
        PaymentSearchSpecification specification = new PaymentSearchSpecification(clubId, filter);
        List<Payment> payments = paymentRepository.findAll(specification, MOST_RECENTLY_PAID_FIRST);

        return paymentMapper.toResponseList(payments);
    }

    @Transactional
    public PaymentResponse voidPayment(AuthenticatedUser currentUser, Long paymentId, VoidPaymentRequest request) {
        Payment payment = findPaymentForUpdateOrThrow(currentUser.clubId(), paymentId);
        boolean alreadyVoided = payment.getVoidedAt() != null;

        if (alreadyVoided) {
            throw new PaymentAlreadyVoidedException(paymentId);
        }

        payment.setVoidedAt(Instant.now());
        payment.setVoidedByUserId(currentUser.userAccountId());
        payment.setVoidReason(normalizeVoidReason(request));
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Voided paymentId={} (memberId={}, periodCovered={}, amount={}) in clubId={} by userAccountId={}", paymentId,
                payment.getMemberId(), payment.getPeriodCovered(), payment.getAmount(), currentUser.clubId(), currentUser.userAccountId());

        return paymentMapper.toResponse(savedPayment);
    }

    public List<MemberDelinquencyResponse> listMemberDelinquency(Long clubId) {
        List<Member> members = memberRepository.findByClubIdAndStatusOrderByCreatedAtDesc(clubId, MemberStatus.ACTIVE);
        Map<Long, LocalDate> lastPaidPeriods = findLastPaidPeriods(members);
        List<MemberDelinquencyResponse> report = new ArrayList<>();

        for (Member member : members) {
            report.add(toDelinquencyResponse(member, lastPaidPeriods.get(member.getId())));
        }

        report.sort(Comparator.comparingLong(MemberDelinquencyResponse::daysOverdue).reversed());

        return report;
    }

    private List<Payment> buildPayments(RecordPaymentRequest request, Long paidByMemberId, Long recordedByUserId) {
        Instant now = Instant.now();
        List<Payment> payments = new ArrayList<>();

        for (LocalDate periodCovered : request.periodsCovered()) {
            Payment payment = Payment.builder()
                    .memberId(request.memberId())
                    .paidByMemberId(paidByMemberId)
                    .amount(request.amount().setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY))
                    .paidAt(now)
                    .periodCovered(periodCovered)
                    .paymentMethod(request.paymentMethod())
                    .recordedByUserId(recordedByUserId)
                    .createdAt(now)
                    .build();
            payments.add(payment);
        }

        return payments;
    }

    private Map<Long, LocalDate> findLastPaidPeriods(List<Member> members) {
        Map<Long, LocalDate> lastPaidPeriods = new HashMap<>();

        if (members.isEmpty()) {
            return lastPaidPeriods;
        }

        List<Long> memberIds = new ArrayList<>();

        for (Member member : members) {
            memberIds.add(member.getId());
        }

        for (MemberLastPaidPeriod lastPaidPeriod : paymentRepository.findLastPaidPeriodByMemberIds(memberIds)) {
            lastPaidPeriods.put(lastPaidPeriod.getMemberId(), lastPaidPeriod.getLastPeriodCovered());
        }

        return lastPaidPeriods;
    }

    private MemberDelinquencyResponse toDelinquencyResponse(Member member, LocalDate lastPeriodCovered) {
        LocalDate coverageStart = lastPeriodCovered != null
                ? lastPeriodCovered.plusMonths(1)
                : member.getJoinedAt().withDayOfMonth(1);
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(coverageStart, LocalDate.now()));

        return new MemberDelinquencyResponse(member.getId(), member.getFirstName(), member.getLastName(), lastPeriodCovered,
                daysOverdue);
    }

    private Long resolvePaidByMemberId(Long clubId, Member member, Long paidByMemberId) {
        if (paidByMemberId == null || paidByMemberId.equals(member.getId())) {
            return null;
        }

        Member paidByMember = findMemberOrThrow(clubId, paidByMemberId);
        boolean sameFamilyGroup = member.getFamilyGroupId() != null
                && member.getFamilyGroupId().equals(paidByMember.getFamilyGroupId());

        if (!sameFamilyGroup) {
            throw new PaidByMemberNotInFamilyGroupException(paidByMemberId, member.getId());
        }

        return paidByMemberId;
    }

    private void validateDistinctPeriods(List<LocalDate> periodsCovered) {
        long distinctCount = periodsCovered.stream().distinct().count();

        if (distinctCount != periodsCovered.size()) {
            throw new DuplicatePeriodCoveredException();
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        boolean bothBoundsPresent = from != null && to != null;

        if (bothBoundsPresent && from.isAfter(to)) {
            throw new InvalidDateRangeException(from, to);
        }
    }

    private String normalizeVoidReason(VoidPaymentRequest request) {
        boolean hasReason = request != null && request.reason() != null && !request.reason().isBlank();

        if (!hasReason) {
            return null;
        }

        return request.reason().strip();
    }

    private Payment findPaymentForUpdateOrThrow(Long clubId, Long paymentId) {
        return paymentRepository
                .findByIdAndClubIdForUpdate(paymentId, clubId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private Member findMemberOrThrow(Long clubId, Long memberId) {
        return memberRepository
                .findByIdAndClubId(memberId, clubId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
