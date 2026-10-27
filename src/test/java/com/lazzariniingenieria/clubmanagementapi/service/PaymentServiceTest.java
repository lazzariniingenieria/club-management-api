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
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentBatchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.VoidPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentAlreadyVoidedException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PeriodAlreadyPaidException;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.PaymentMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberLastPaidPeriod;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long ACTING_USER_ID = 5L;
    private static final Long OTHER_USER_ID = 6L;
    private static final Long PAYMENT_ID = 100L;
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
                .isInstanceOf(DuplicatePeriodCoveredException.class)
                .hasMessageContaining("memberId " + MEMBER_ID);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldThrowPeriodAlreadyPaidWhenMemberAlreadyHasAnActivePaymentForARequestedPeriod() {
        LocalDate period = LocalDate.parse("2026-07-01");
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullAndPeriodCoveredIn(MEMBER_ID, List.of(period)))
                .thenReturn(List.of(payment(MEMBER_ID, period)));

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request))
                .isInstanceOf(PeriodAlreadyPaidException.class)
                .hasMessageContaining("2026-07-01");

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldRejectTheWholeRequestWhenOnlyOneOfMultiplePeriodsIsAlreadyPaid() {
        LocalDate alreadyPaidPeriod = LocalDate.parse("2026-06-01");
        LocalDate newPeriod = LocalDate.parse("2026-07-01");
        List<LocalDate> periods = List.of(alreadyPaidPeriod, newPeriod);
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, periods, PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullAndPeriodCoveredIn(MEMBER_ID, periods))
                .thenReturn(List.of(payment(MEMBER_ID, alreadyPaidPeriod)));

        assertThatThrownBy(() -> paymentService.recordPayment(CURRENT_USER, request)).isInstanceOf(PeriodAlreadyPaidException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldAllowRecordingPaymentWhenTheOnlyExistingPaymentForThatPeriodWasVoided() {
        LocalDate period = LocalDate.parse("2026-07-01");
        RecordPaymentRequest request = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullAndPeriodCoveredIn(MEMBER_ID, List.of(period))).thenReturn(List.of());
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPayment(CURRENT_USER, request);

        assertThat(response).hasSize(1);
    }

    @Test
    void shouldRecordEveryEntryOfTheBatchAndReturnAllPaymentsTogether() {
        LocalDate period = LocalDate.parse("2026-07-01");
        RecordPaymentRequest firstEntry = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        RecordPaymentRequest secondEntry = new RecordPaymentRequest(OTHER_MEMBER_ID, MEMBER_ID, AMOUNT, List.of(period),
                PaymentMethod.CASH);
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, 9L)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, 9L)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PaymentResponse> response = paymentService.recordPaymentBatch(CURRENT_USER,
                new RecordPaymentBatchRequest(List.of(firstEntry, secondEntry)));

        assertThat(response).extracting(PaymentResponse::memberId).containsExactly(MEMBER_ID, OTHER_MEMBER_ID);
        assertThat(response).extracting(PaymentResponse::paidByMemberId).containsExactly(null, MEMBER_ID);
        assertThat(response).extracting(PaymentResponse::recordedByUserId).containsOnly(ACTING_USER_ID);
    }

    @Test
    void shouldFailTheWholeBatchNamingTheMemberWhenOneEntryHasAPeriodAlreadyPaid() {
        LocalDate period = LocalDate.parse("2026-07-01");
        RecordPaymentRequest firstEntry = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        RecordPaymentRequest secondEntry = new RecordPaymentRequest(OTHER_MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        RecordPaymentBatchRequest request = new RecordPaymentBatchRequest(List.of(firstEntry, secondEntry));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(memberRepository.findByIdAndClubId(OTHER_MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(OTHER_MEMBER_ID, null)));
        when(paymentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullAndPeriodCoveredIn(MEMBER_ID, List.of(period))).thenReturn(List.of());
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullAndPeriodCoveredIn(OTHER_MEMBER_ID, List.of(period)))
                .thenReturn(List.of(payment(OTHER_MEMBER_ID, period)));

        assertThatThrownBy(() -> paymentService.recordPaymentBatch(CURRENT_USER, request))
                .isInstanceOf(PeriodAlreadyPaidException.class)
                .hasMessageContaining("memberId " + OTHER_MEMBER_ID);
    }

    @Test
    void shouldFailTheWholeBatchWhenAnEntryBelongsToAMemberOutsideTheClub() {
        LocalDate period = LocalDate.parse("2026-07-01");
        RecordPaymentRequest entry = new RecordPaymentRequest(MEMBER_ID, null, AMOUNT, List.of(period), PaymentMethod.CASH);
        RecordPaymentBatchRequest request = new RecordPaymentBatchRequest(List.of(entry));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.recordPaymentBatch(CURRENT_USER, request)).isInstanceOf(MemberNotFoundException.class);

        verify(paymentRepository, never()).saveAll(any());
    }

    @Test
    void shouldListOnlyNonVoidedPaymentsForMemberByDefault() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdAndVoidedAtIsNullOrderByPeriodCoveredDesc(MEMBER_ID)).thenReturn(List.of(payment(MEMBER_ID,
                LocalDate.parse("2026-07-01"))));

        List<PaymentResponse> response = paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).periodCovered()).isEqualTo(LocalDate.parse("2026-07-01"));
        verify(paymentRepository, never()).findByMemberIdOrderByPeriodCoveredDesc(any());
    }

    @Test
    void shouldListVoidedPaymentsForMemberWhenIncludeVoidedIsRequested() {
        Payment voidedPayment = voidedPayment(MEMBER_ID, LocalDate.parse("2026-07-01"));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MEMBER_ID, null)));
        when(paymentRepository.findByMemberIdOrderByPeriodCoveredDesc(MEMBER_ID)).thenReturn(List.of(voidedPayment));

        List<PaymentResponse> response = paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, true);

        assertThat(response.get(0).voidedAt()).isEqualTo(voidedPayment.getVoidedAt());
        verify(paymentRepository, never()).findByMemberIdAndVoidedAtIsNullOrderByPeriodCoveredDesc(any());
    }

    @Test
    void shouldThrowMemberNotFoundWhenListingPaymentsForMissingMember() {
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, false))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldSearchWithEveryCombinedFilterScopedToTheClubOfTheToken() {
        PaymentSearchRequest filter = new PaymentSearchRequest(MEMBER_ID, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-01"),
                PaymentMethod.TRANSFER, false);
        when(paymentRepository.findAll(any(PaymentSearchSpecification.class), any(Sort.class))).thenReturn(List.of(payment(MEMBER_ID,
                LocalDate.parse("2026-03-01"))));

        List<PaymentResponse> response = paymentService.searchPayments(CLUB_ID, filter);

        ArgumentCaptor<PaymentSearchSpecification> specificationCaptor = ArgumentCaptor.forClass(PaymentSearchSpecification.class);
        verify(paymentRepository).findAll(specificationCaptor.capture(), any(Sort.class));
        assertThat(specificationCaptor.getValue().getClubId()).isEqualTo(CLUB_ID);
        assertThat(specificationCaptor.getValue().getFilter()).isEqualTo(filter);
        assertThat(response).hasSize(1);
    }

    @Test
    void shouldSortSearchResultsByPaidAtDescendingWithDeterministicTieBreakers() {
        when(paymentRepository.findAll(any(PaymentSearchSpecification.class), any(Sort.class))).thenReturn(List.of());

        paymentService.searchPayments(CLUB_ID, emptySearch());

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(paymentRepository).findAll(any(PaymentSearchSpecification.class), sortCaptor.capture());
        assertThat(sortCaptor.getValue().toList())
                .containsExactly(Sort.Order.desc("paidAt"), Sort.Order.desc("periodCovered"), Sort.Order.desc("id"));
    }

    @Test
    void shouldAcceptSearchWhenFromEqualsTo() {
        LocalDate period = LocalDate.parse("2026-07-01");
        PaymentSearchRequest filter = new PaymentSearchRequest(null, period, period, null, false);
        when(paymentRepository.findAll(any(PaymentSearchSpecification.class), any(Sort.class))).thenReturn(List.of());

        List<PaymentResponse> response = paymentService.searchPayments(CLUB_ID, filter);

        assertThat(response).isEmpty();
    }

    @Test
    void shouldRejectSearchWhenFromIsAfterToWithoutQueryingPayments() {
        PaymentSearchRequest filter = new PaymentSearchRequest(null, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-06-01"), null,
                false);

        assertThatThrownBy(() -> paymentService.searchPayments(CLUB_ID, filter)).isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldMarkPaymentAsVoidedByTheActingUserWithTrimmedReason() {
        Payment payment = payment(MEMBER_ID, LocalDate.parse("2026-07-01"));
        stubPaymentForUpdate(payment);

        PaymentResponse response = paymentService.voidPayment(CURRENT_USER, PAYMENT_ID, new VoidPaymentRequest("  Importe mal cargado  "));

        verify(paymentRepository).save(payment);
        assertThat(payment.getVoidedAt()).isNotNull();
        assertThat(payment.getVoidedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(payment.getVoidReason()).isEqualTo("Importe mal cargado");
        assertThat(response.voidedAt()).isEqualTo(payment.getVoidedAt());
    }

    @Test
    void shouldStoreNullReasonWhenVoidingWithoutRequestBody() {
        Payment payment = payment(MEMBER_ID, LocalDate.parse("2026-07-01"));
        stubPaymentForUpdate(payment);

        paymentService.voidPayment(CURRENT_USER, PAYMENT_ID, null);

        assertThat(payment.getVoidedAt()).isNotNull();
        assertThat(payment.getVoidReason()).isNull();
    }

    @Test
    void shouldStoreNullReasonWhenVoidReasonIsBlank() {
        Payment payment = payment(MEMBER_ID, LocalDate.parse("2026-07-01"));
        stubPaymentForUpdate(payment);

        paymentService.voidPayment(CURRENT_USER, PAYMENT_ID, new VoidPaymentRequest("   "));

        assertThat(payment.getVoidReason()).isNull();
    }

    @Test
    void shouldRejectVoidingAnAlreadyVoidedPaymentWithoutOverwritingTheOriginalVoid() {
        Payment alreadyVoided = voidedPayment(MEMBER_ID, LocalDate.parse("2026-07-01"));
        Instant originalVoidedAt = alreadyVoided.getVoidedAt();
        when(paymentRepository.findByIdAndClubIdForUpdate(PAYMENT_ID, CLUB_ID)).thenReturn(Optional.of(alreadyVoided));

        assertThatThrownBy(() -> paymentService.voidPayment(CURRENT_USER, PAYMENT_ID, null))
                .isInstanceOf(PaymentAlreadyVoidedException.class);

        verify(paymentRepository, never()).save(any());
        assertThat(alreadyVoided.getVoidedAt()).isEqualTo(originalVoidedAt);
        assertThat(alreadyVoided.getVoidedByUserId()).isEqualTo(OTHER_USER_ID);
    }

    @Test
    void shouldThrowPaymentNotFoundWhenVoidingPaymentOutsideTheClub() {
        when(paymentRepository.findByIdAndClubIdForUpdate(PAYMENT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.voidPayment(CURRENT_USER, PAYMENT_ID, null)).isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository, never()).save(any());
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

    private void stubPaymentForUpdate(Payment payment) {
        when(paymentRepository.findByIdAndClubIdForUpdate(PAYMENT_ID, CLUB_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
    }

    private PaymentSearchRequest emptySearch() {
        return new PaymentSearchRequest(null, null, null, null, false);
    }

    private Payment voidedPayment(Long memberId, LocalDate periodCovered) {
        Payment payment = payment(memberId, periodCovered);
        payment.setVoidedAt(Instant.parse("2026-07-02T10:00:00Z"));
        payment.setVoidedByUserId(OTHER_USER_ID);

        return payment;
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
                .id(PAYMENT_ID)
                .memberId(memberId)
                .amount(AMOUNT)
                .periodCovered(periodCovered)
                .paymentMethod(PaymentMethod.CASH)
                .recordedByUserId(ACTING_USER_ID)
                .build();
    }
}
