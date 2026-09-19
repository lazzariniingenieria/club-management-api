package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final PaymentMapper paymentMapper;

    @Transactional
    public List<PaymentResponse> recordPayment(AuthenticatedUser currentUser, RecordPaymentRequest request) {
        Long clubId = currentUser.clubId();
        Member member = findMemberOrThrow(clubId, request.memberId());
        validatePaidByMember(clubId, member, request.paidByMemberId());
        validateDistinctPeriods(request.periodsCovered());

        List<Payment> payments = buildPayments(currentUser, request, Instant.now());
        List<Payment> savedPayments = paymentRepository.saveAll(payments);
        log.info("Recorded {} payment(s) for memberId={} in clubId={}", savedPayments.size(), request.memberId(), clubId);

        return paymentMapper.toResponseList(savedPayments);
    }

    public List<PaymentResponse> listPaymentsForMember(Long clubId, Long memberId) {
        findMemberOrThrow(clubId, memberId);
        List<Payment> payments = paymentRepository.findByMemberIdOrderByPeriodCoveredDesc(memberId);

        return paymentMapper.toResponseList(payments);
    }

    public List<MemberDelinquencyResponse> listMemberDelinquency(Long clubId) {
        List<Member> members = memberRepository.findByClubIdOrderByCreatedAtDesc(clubId);
        List<MemberDelinquencyResponse> report = members.stream()
                .map(this::toDelinquencyResponse)
                .sorted(Comparator.comparingLong(MemberDelinquencyResponse::daysOverdue).reversed())
                .toList();

        return report;
    }

    private List<Payment> buildPayments(AuthenticatedUser currentUser, RecordPaymentRequest request, Instant now) {
        List<Payment> payments = new ArrayList<>();

        for (LocalDate periodCovered : request.periodsCovered()) {
            Payment payment = Payment.builder()
                    .memberId(request.memberId())
                    .paidByMemberId(request.paidByMemberId())
                    .amount(request.amount())
                    .paidAt(now)
                    .periodCovered(periodCovered)
                    .paymentMethod(request.paymentMethod())
                    .recordedByUserId(currentUser.userAccountId())
                    .createdAt(now)
                    .build();
            payments.add(payment);
        }

        return payments;
    }

    private MemberDelinquencyResponse toDelinquencyResponse(Member member) {
        Optional<Payment> lastPayment = paymentRepository.findTopByMemberIdOrderByPeriodCoveredDesc(member.getId());
        LocalDate coverageStart = lastPayment
                .map(payment -> payment.getPeriodCovered().plusMonths(1))
                .orElseGet(() -> member.getJoinedAt().withDayOfMonth(1));
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(coverageStart, LocalDate.now()));
        LocalDate lastPeriodCovered = lastPayment.map(Payment::getPeriodCovered).orElse(null);

        return new MemberDelinquencyResponse(member.getId(), member.getFirstName(), member.getLastName(), lastPeriodCovered,
                daysOverdue);
    }

    private void validatePaidByMember(Long clubId, Member member, Long paidByMemberId) {
        if (paidByMemberId == null || paidByMemberId.equals(member.getId())) {
            return;
        }

        Member paidByMember = memberRepository
                .findByIdAndClubId(paidByMemberId, clubId)
                .orElseThrow(() -> new MemberNotFoundException(paidByMemberId));
        boolean sameFamilyGroup = member.getFamilyGroupId() != null
                && member.getFamilyGroupId().equals(paidByMember.getFamilyGroupId());

        if (!sameFamilyGroup) {
            throw new PaidByMemberNotInFamilyGroupException(paidByMemberId, member.getId());
        }
    }

    private void validateDistinctPeriods(List<LocalDate> periodsCovered) {
        long distinctCount = periodsCovered.stream().distinct().count();

        if (distinctCount != periodsCovered.size()) {
            throw new DuplicatePeriodCoveredException();
        }
    }

    private Member findMemberOrThrow(Long clubId, Long memberId) {
        return memberRepository
                .findByIdAndClubId(memberId, clubId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
