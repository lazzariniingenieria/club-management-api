package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceipt;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptLine;
import com.lazzariniingenieria.clubmanagementapi.report.FeeReceiptPdfRenderer;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeReceiptServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long FAMILY_GROUP_ID = 10L;
    private static final Long OTHER_FAMILY_GROUP_ID = 11L;
    private static final String CLUB_NAME = "Club Atlético Jorge Newbery";
    private static final LocalDate PAID_FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate PAID_TO = LocalDate.parse("2026-10-15");
    private static final LocalDate JULY = LocalDate.parse("2026-07-01");
    private static final LocalDate AUGUST = LocalDate.parse("2026-08-01");
    private static final BigDecimal FEE = new BigDecimal("2000.00");
    private static final byte[] PDF = {37, 80, 68, 70};

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private FeeReceiptPdfRenderer feeReceiptPdfRenderer;

    private FeeReceiptService feeReceiptService;

    @BeforeEach
    void setUp() {
        feeReceiptService = new FeeReceiptService(paymentRepository, memberRepository, clubRepository, feeReceiptPdfRenderer);
    }

    @Test
    void shouldReturnEmptyWithoutRenderingWhenNoPaymentsWereCollectedInTheRange() {
        when(paymentRepository.findActiveByClubIdAndPaidAtInRange(eq(CLUB_ID), any(), any())).thenReturn(List.of());

        Optional<byte[]> pdf = feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO);

        assertThat(pdf).isEmpty();
        verifyNoInteractions(memberRepository, clubRepository, feeReceiptPdfRenderer);
    }

    @Test
    void shouldRejectRangeWhenPaidFromIsAfterPaidToWithoutQueryingPayments() {
        assertThatThrownBy(() -> feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_TO, PAID_FROM))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("paidFrom 2026-10-15 must not be after paidTo 2026-10-01");

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldAcceptRangeWhenPaidFromEqualsPaidTo() {
        when(paymentRepository.findActiveByClubIdAndPaidAtInRange(eq(CLUB_ID), any(), any())).thenReturn(List.of());

        Optional<byte[]> pdf = feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_FROM);

        assertThat(pdf).isEmpty();
    }

    @Test
    void shouldQueryFromStartOfPaidFromUntilStartOfTheDayAfterPaidToInArgentinaTime() {
        when(paymentRepository.findActiveByClubIdAndPaidAtInRange(eq(CLUB_ID), any(), any())).thenReturn(List.of());

        feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO);

        verify(paymentRepository).findActiveByClubIdAndPaidAtInRange(CLUB_ID, Instant.parse("2026-10-01T03:00:00Z"),
                Instant.parse("2026-10-16T03:00:00Z"));
    }

    @Test
    void shouldLookUpMembersOnlyWithinTheClubOfTheToken() {
        Member andres = member(1L, FAMILY_GROUP_ID, "Lazzarini", "Andres");
        Member bautista = member(2L, FAMILY_GROUP_ID, "Lazzarini", "Bautista");
        stubCollectedPayments(List.of(payment(andres, JULY, FEE), payment(bautista, JULY, FEE)), andres, bautista);

        feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO);

        verify(memberRepository).findByClubIdAndIdIn(CLUB_ID, Set.of(1L, 2L));
    }

    @Test
    void shouldReturnThePdfProducedByTheRendererUnderTheClubName() {
        Member bautista = member(1L, null, "Lazzarini", "Bautista");
        stubCollectedPayments(List.of(payment(bautista, JULY, FEE)), bautista);

        Optional<byte[]> pdf = feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO);

        assertThat(pdf).contains(PDF);
        verify(feeReceiptPdfRenderer).render(eq(CLUB_NAME), anyList());
    }

    @Test
    void shouldGroupMembersOfTheSameFamilyGroupAndPeriodIntoOneReceipt() {
        Member andres = member(1L, FAMILY_GROUP_ID, "Lazzarini", "Andres");
        Member bautista = member(2L, FAMILY_GROUP_ID, "Lazzarini", "Bautista");
        stubCollectedPayments(List.of(payment(andres, JULY, FEE), payment(bautista, JULY, FEE)), andres, bautista);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts).hasSize(1);
        assertThat(memberNames(receipts.getFirst())).containsExactly("Lazzarini Andres", "Lazzarini Bautista");
    }

    @Test
    void shouldGiveEachMemberWithoutFamilyGroupAReceiptOfTheirOwn() {
        Member fabian = member(1L, null, "Andrada", "Fabian");
        Member juan = member(2L, null, "Arroyo", "Juan");
        stubCollectedPayments(List.of(payment(fabian, JULY, FEE), payment(juan, JULY, FEE)), fabian, juan);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts).hasSize(2);
        assertThat(memberNames(receipts.get(0))).containsExactly("Andrada Fabian");
        assertThat(memberNames(receipts.get(1))).containsExactly("Arroyo Juan");
    }

    @Test
    void shouldSplitAFamilyGroupIntoOneReceiptPerPeriodCovered() {
        Member bautista = member(1L, FAMILY_GROUP_ID, "Lazzarini", "Bautista");
        stubCollectedPayments(List.of(payment(bautista, JULY, FEE), payment(bautista, AUGUST, FEE)), bautista);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts).extracting(FeeReceipt::periodCovered).containsExactly(JULY, AUGUST);
    }

    @Test
    void shouldTotalTheAmountsOfEveryMemberInTheReceipt() {
        Member juan = member(1L, FAMILY_GROUP_ID, "Sodero", "Juan");
        Member alexis = member(2L, FAMILY_GROUP_ID, "Sodero", "Alexis");
        stubCollectedPayments(List.of(payment(juan, JULY, FEE), payment(alexis, JULY, new BigDecimal("1500.00"))), juan, alexis);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts.getFirst().totalAmount()).isEqualByComparingTo("3500");
    }

    @Test
    void shouldKeepTheAmountEachMemberActuallyPaidOnTheirLine() {
        Member juan = member(1L, FAMILY_GROUP_ID, "Sodero", "Juan");
        Member alexis = member(2L, FAMILY_GROUP_ID, "Sodero", "Alexis");
        stubCollectedPayments(List.of(payment(juan, JULY, FEE), payment(alexis, JULY, new BigDecimal("1500.00"))), juan, alexis);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts.getFirst().lines()).extracting(FeeReceiptLine::amount).containsExactly(new BigDecimal("1500.00"), FEE);
    }

    @Test
    void shouldSortMembersWithinAReceiptAlphabeticallyInSpanishSoAccentedNamesAreNotPushedToTheEnd() {
        Member benitez = member(1L, FAMILY_GROUP_ID, "Benítez", "Ana");
        Member avila = member(2L, FAMILY_GROUP_ID, "Ávila", "Rosa");
        stubCollectedPayments(List.of(payment(benitez, JULY, FEE), payment(avila, JULY, FEE)), benitez, avila);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(memberNames(receipts.getFirst())).containsExactly("Ávila Rosa", "Benítez Ana");
    }

    @Test
    void shouldOrderReceiptsByTheirFirstMemberAndThenByPeriod() {
        Member zeballos = member(1L, FAMILY_GROUP_ID, "Zeballos", "Sofia");
        Member arcando = member(2L, FAMILY_GROUP_ID, "Arcando", "Santiago");
        Member alcaraz = member(3L, OTHER_FAMILY_GROUP_ID, "Alcaraz", "Diego");
        List<Payment> payments = List.of(payment(zeballos, AUGUST, FEE), payment(alcaraz, JULY, FEE), payment(zeballos, JULY, FEE),
                payment(arcando, JULY, FEE));
        stubCollectedPayments(payments, zeballos, arcando, alcaraz);

        List<FeeReceipt> receipts = generateAndCaptureReceipts();

        assertThat(receipts).extracting(receipt -> memberNames(receipt).getFirst() + " " + receipt.periodCovered())
                .containsExactly("Alcaraz Diego 2026-07-01", "Arcando Santiago 2026-07-01", "Zeballos Sofia 2026-08-01");
    }

    @Test
    void shouldThrowClubNotFoundWhenTheClubOfTheTokenHasNoRow() {
        Member bautista = member(1L, null, "Lazzarini", "Bautista");
        when(paymentRepository.findActiveByClubIdAndPaidAtInRange(eq(CLUB_ID), any(), any()))
                .thenReturn(List.of(payment(bautista, JULY, FEE)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO))
                .isInstanceOf(ClubNotFoundException.class);

        verifyNoInteractions(feeReceiptPdfRenderer);
    }

    private List<FeeReceipt> generateAndCaptureReceipts() {
        feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO);
        ArgumentCaptor<List<FeeReceipt>> receiptsCaptor = ArgumentCaptor.forClass(List.class);
        verify(feeReceiptPdfRenderer).render(eq(CLUB_NAME), receiptsCaptor.capture());

        return receiptsCaptor.getValue();
    }

    private List<String> memberNames(FeeReceipt receipt) {
        List<String> memberNames = new ArrayList<>();

        for (FeeReceiptLine line : receipt.lines()) {
            memberNames.add(line.memberName());
        }

        return memberNames;
    }

    private void stubCollectedPayments(List<Payment> payments, Member... members) {
        when(paymentRepository.findActiveByClubIdAndPaidAtInRange(eq(CLUB_ID), any(), any())).thenReturn(payments);
        when(memberRepository.findByClubIdAndIdIn(eq(CLUB_ID), any())).thenReturn(List.of(members));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club()));
        when(feeReceiptPdfRenderer.render(anyString(), anyList())).thenReturn(PDF);
    }

    private Club club() {
        return Club.builder()
                .id(CLUB_ID)
                .name(CLUB_NAME)
                .build();
    }

    private Member member(Long id, Long familyGroupId, String lastName, String firstName) {
        return Member.builder()
                .id(id)
                .clubId(CLUB_ID)
                .familyGroupId(familyGroupId)
                .firstName(firstName)
                .lastName(lastName)
                .dni("3011122" + id)
                .joinedAt(LocalDate.parse("2026-01-01"))
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private Payment payment(Member member, LocalDate periodCovered, BigDecimal amount) {
        return Payment.builder()
                .memberId(member.getId())
                .amount(amount)
                .paidAt(Instant.parse("2026-10-05T15:00:00Z"))
                .periodCovered(periodCovered)
                .paymentMethod(PaymentMethod.CASH)
                .recordedByUserId(5L)
                .build();
    }
}
