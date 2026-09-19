package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberLastPaidPeriod;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long ACTING_USER_ID = 5L;
    private static final BigDecimal AMOUNT = new BigDecimal("15000.00");
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MemberRepository memberRepository;

    private final PaymentMapper paymentMapper = new PaymentMapperImpl();

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, memberRepository, paymentMapper);
    }

    @Test
    void shouldRecordSinglePaymentWhenMemberExists() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(LocalDate.parse("2026-07-01")),
                PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        ArgumentCaptor<List<Payment>> savedPaymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentRepository).saveAll(savedPaymentsCaptor.capture());
        List<Payment> savedPayments = savedPaymentsCaptor.getValue();

        assertThat(savedPayments).hasSize(1);
        assertThat(savedPayments.get(0).getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(savedPayments.get(0).getPaidByMemberId()).isNull();
        assertThat(savedPayments.get(0).getAmount()).isEqualTo(AMOUNT);
        assertThat(savedPayments.get(0).getPeriodCovered()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(savedPayments.get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(savedPayments.get(0).getRecordedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.get(0).periodCovered()).isEqualTo(LocalDate.parse("2026-07-01"));
    }

    @Test
    void shouldNormalizeAmountToTwoDecimalPlacesSoResponseMatchesWhatTheDatabaseStores() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, new BigDecimal("15000"),
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        assertThat(response.get(0).amount().scale()).isEqualTo(2);
        assertThat(response.get(0).amount()).isEqualByComparingTo("15000");
    }

    @Test
    void shouldRecordOnePaymentRowPerPeriodInASingleSaveAllCallWhenMultiplePeriodsCovered() {
        List<LocalDate> periods = List.of(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01"));
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, periods, PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        ArgumentCaptor<List<Payment>> savedPaymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentRepository).saveAll(savedPaymentsCaptor.capture());
        List<Payment> savedPayments = savedPaymentsCaptor.getValue();

        assertThat(savedPayments).hasSize(2);
        assertThat(savedPayments).extracting(Payment::getPeriodCovered).containsExactlyElementsOf(periods);
        assertThat(response).hasSize(2);
    }

    @Test
    void shouldThrowMemberNotFoundWhenRecordingPaymentForMissingMember() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(LocalDate.parse("2026-07-01")),
                PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request)).isInstanceOf(MemberNotFoundException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldRecordPaymentWhenPaidByMemberIsInSameFamilyGroup() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, OTHER_MEMBER_ID, AMOUNT,
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.TRANSFER);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, 9L)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, 9L)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        assertThat(response.get(0).paidByMemberId()).isEqualTo(OTHER_MEMBER_ID);
    }

    @Test
    void shouldNormalizePaidByMemberIdToNullWhenSameAsPayingMember() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, MEMBER_ID, AMOUNT,
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        assertThat(response.get(0).paidByMemberId()).isNull();
        verify(memberRepository, times(1)).findByIdAndClubId(any(), any());
    }

    @Test
    void shouldThrowMemberNotFoundWhenPaidByMemberDoesNotExist() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, OTHER_MEMBER_ID, AMOUNT,
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, 9L)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request)).isInstanceOf(MemberNotFoundException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldThrowPaidByMemberNotInFamilyGroupWhenFamilyGroupsDiffer() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, OTHER_MEMBER_ID, AMOUNT,
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, 9L)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, 42L)));

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request))
                .isInstanceOf(PaidByMemberNotInFamilyGroupException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldThrowPaidByMemberNotInFamilyGroupWhenPayingMemberHasNoFamilyGroup() {
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, OTHER_MEMBER_ID, AMOUNT,
                List.of(LocalDate.parse("2026-07-01")), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, null)));

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request))
                .isInstanceOf(PaidByMemberNotInFamilyGroupException.class);
    }

    @Test
    void shouldThrowDuplicatePeriodCoveredWhenPeriodsContainDuplicates() {
        List<LocalDate> periods = List.of(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"));
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, periods, PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request))
                .isInstanceOf(DuplicatePeriodCoveredException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldListPaymentsForMemberOrderedByPeriodCoveredDescending() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdOrderByPeriodCoveredDesc(MEMBER_ID)).thenReturn(List.of(payment(MEMBER_ID,
                LocalDate.parse("2026-07-01"))));

        List<PaymentResponse> response = paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).periodCovered()).isEqualTo(LocalDate.parse("2026-07-01"));
    }

    @Test
    void shouldThrowMemberNotFoundWhenListingPaymentsForMissingMember() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldSortMemberDelinquencyByDaysOverdueDescending() {
        LocalDate today = LocalDate.now();
        stubActiveMembers(member(MEMBER_ID, null), member(OTHER_MEMBER_ID, null));
        when(paymentRepository.findLastPaidPeriodByMemberIds(any())).thenReturn(List.of(
                lastPaidPeriod(MEMBER_ID, today.withDayOfMonth(1)),
                lastPaidPeriod(OTHER_MEMBER_ID, today.minusMonths(3).withDayOfMonth(1))));

        List<MemberDelinquencyResponse> report = paymentService.listMemberDelinquency(CLUB_ID);

        assertThat(report).hasSize(2);
        assertThat(report.get(0).memberId()).isEqualTo(OTHER_MEMBER_ID);
        assertThat(report.get(0).daysOverdue()).isGreaterThan(report.get(1).daysOverdue());
    }

    @Test
    void shouldQueryOnlyActiveMembersAndResolveLastPaidPeriodsInASingleQuery() {
        stubActiveMembers(member(MEMBER_ID, null), member(OTHER_MEMBER_ID, null));
        when(paymentRepository.findLastPaidPeriodByMemberIds(any())).thenReturn(List.of());

        paymentService.listMemberDelinquency(CLUB_ID);

        verify(memberRepository).findByClubIdAndStatusOrderByCreatedAtDesc(CLUB_ID, MemberStatus.ACTIVE);
        verify(paymentRepository, times(1)).findLastPaidPeriodByMemberIds(List.of(MEMBER_ID, OTHER_MEMBER_ID));
    }

    @Test
    void shouldReturnEmptyDelinquencyReportWithoutQueryingPaymentsWhenClubHasNoActiveMembers() {
        stubActiveMembers();

        List<MemberDelinquencyResponse> report = paymentService.listMemberDelinquency(CLUB_ID);

        assertThat(report).isEmpty();
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldComputeDaysOverdueFromLastPaidPeriodWhenMemberHasPayments() {
        LocalDate today = LocalDate.now();
        LocalDate lastPeriod = today.minusMonths(2).withDayOfMonth(1);
        long expectedDaysOverdue = ChronoUnit.DAYS.between(lastPeriod.plusMonths(1), today);
        stubActiveMembers(member(MEMBER_ID, null));
        when(paymentRepository.findLastPaidPeriodByMemberIds(any())).thenReturn(List.of(lastPaidPeriod(MEMBER_ID, lastPeriod)));

        List<MemberDelinquencyResponse> report = paymentService.listMemberDelinquency(CLUB_ID);

        assertThat(report.get(0).lastPeriodCovered()).isEqualTo(lastPeriod);
        assertThat(report.get(0).daysOverdue()).isEqualTo(expectedDaysOverdue);
    }

    @Test
    void shouldComputeDaysOverdueFromJoinedAtWhenMemberNeverPaid() {
        LocalDate today = LocalDate.now();
        LocalDate joinedAt = today.minusMonths(4).withDayOfMonth(15);
        long expectedDaysOverdue = ChronoUnit.DAYS.between(joinedAt.withDayOfMonth(1), today);
        Member neverPaidMember = member(MEMBER_ID, null);
        neverPaidMember.setJoinedAt(joinedAt);
        stubActiveMembers(neverPaidMember);
        when(paymentRepository.findLastPaidPeriodByMemberIds(any())).thenReturn(List.of());

        List<MemberDelinquencyResponse> report = paymentService.listMemberDelinquency(CLUB_ID);

        assertThat(report.get(0).lastPeriodCovered()).isNull();
        assertThat(report.get(0).daysOverdue()).isEqualTo(expectedDaysOverdue);
    }

    @Test
    void shouldReportZeroDaysOverdueWhenLastPaymentCoversAFuturePeriod() {
        LocalDate futurePeriod = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        stubActiveMembers(member(MEMBER_ID, null));
        when(paymentRepository.findLastPaidPeriodByMemberIds(any())).thenReturn(List.of(lastPaidPeriod(MEMBER_ID, futurePeriod)));

        List<MemberDelinquencyResponse> report = paymentService.listMemberDelinquency(CLUB_ID);

        assertThat(report.get(0).daysOverdue()).isZero();
    }

    private void stubActiveMembers(Member... members) {
        when(memberRepository.findByClubIdAndStatusOrderByCreatedAtDesc(CLUB_ID, MemberStatus.ACTIVE)).thenReturn(List.of(members));
    }

    private MemberLastPaidPeriod lastPaidPeriod(Long memberId, LocalDate lastPeriodCovered) {
        return new MemberLastPaidPeriod() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public LocalDate getLastPeriodCovered() {
                return lastPeriodCovered;
            }
        };
    }

    private Member member(Long id, Long familyGroupId) {
        return Member.builder()
                .id(id)
                .clubId(CLUB_ID)
                .familyGroupId(familyGroupId)
                .firstName("Marcos")
                .lastName("Gomez")
                .dni("3011122" + id)
                .joinedAt(LocalDate.parse("2026-01-01"))
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private Payment payment(Long memberId, LocalDate periodCovered) {
        return Payment.builder()
                .id(100L)
                .memberId(memberId)
                .amount(AMOUNT)
                .periodCovered(periodCovered)
                .paymentMethod(PaymentMethod.CASH)
                .recordedByUserId(ACTING_USER_ID)
                .build();
    }
}
