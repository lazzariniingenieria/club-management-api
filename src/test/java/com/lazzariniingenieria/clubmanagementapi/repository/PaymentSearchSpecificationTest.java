package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSearchSpecificationTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private static final LocalDate FROM = LocalDate.parse("2026-01-01");
    private static final LocalDate TO = LocalDate.parse("2026-06-01");

    @Mock
    private Root<Payment> payment;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Subquery<Long> clubMemberIds;

    @Mock
    private Root<Member> member;

    @Mock
    private Path<Object> paymentMemberIdPath;

    @Mock
    private Path<Object> voidedAtPath;

    @Mock
    private Path<LocalDate> periodCoveredPath;

    @Mock
    private Path<Object> paymentMethodPath;

    @Mock
    private Path<Object> memberClubIdPath;

    @Mock
    private Predicate belongsToClub;

    @Mock
    private Predicate notVoided;

    @Mock
    private Predicate matchesMember;

    @Mock
    private Predicate fromPeriod;

    @Mock
    private Predicate toPeriod;

    @Mock
    private Predicate matchesMethod;

    @Mock
    private Predicate combined;

    @BeforeEach
    void setUp() {
        stubPaths();
        stubPredicates();
    }

    @Test
    void shouldScopeToClubAndExcludeVoidedPaymentsWhenNoFilterIsGiven() {
        Predicate result = toPredicate(new PaymentSearchRequest(null, null, null, null, false));

        verify(builder).equal(memberClubIdPath, CLUB_ID);
        verify(builder).and(new Predicate[] {belongsToClub, notVoided});
        assertThat(result).isSameAs(combined);
    }

    @Test
    void shouldTreatMissingIncludeVoidedAsExcludingVoidedPayments() {
        toPredicate(new PaymentSearchRequest(null, null, null, null, null));

        verify(builder).and(new Predicate[] {belongsToClub, notVoided});
    }

    @Test
    void shouldKeepVoidedPaymentsWhenIncludeVoidedIsRequested() {
        toPredicate(new PaymentSearchRequest(null, null, null, null, true));

        verify(builder, never()).isNull(any());
        verify(builder).and(belongsToClub);
    }

    @Test
    void shouldCombineEveryFilterWhenAllAreGiven() {
        toPredicate(new PaymentSearchRequest(MEMBER_ID, FROM, TO, PaymentMethod.CASH, false));

        verify(builder).and(belongsToClub, notVoided, matchesMember, fromPeriod, toPeriod, matchesMethod);
    }

    @Test
    void shouldCombineMemberAndDateRangeWithoutFilteringByMethod() {
        toPredicate(new PaymentSearchRequest(MEMBER_ID, FROM, TO, null, false));

        verify(builder).and(belongsToClub, notVoided, matchesMember, fromPeriod, toPeriod);
    }

    @Test
    void shouldApplyOnlyTheLowerPeriodBoundWhenToIsMissing() {
        toPredicate(new PaymentSearchRequest(null, FROM, null, null, false));

        verify(builder, never()).lessThanOrEqualTo(any(), any(LocalDate.class));
        verify(builder).and(belongsToClub, notVoided, fromPeriod);
    }

    @Test
    void shouldCombineMethodWithVoidedPaymentsWhenBothAreRequested() {
        toPredicate(new PaymentSearchRequest(null, null, TO, PaymentMethod.TRANSFER, true));

        verify(builder).equal(paymentMethodPath, PaymentMethod.TRANSFER);
        verify(builder).and(belongsToClub, toPeriod, matchesMethod);
    }

    private Predicate toPredicate(PaymentSearchRequest filter) {
        PaymentSearchSpecification specification = new PaymentSearchSpecification(CLUB_ID, filter);

        return specification.toPredicate(payment, query, builder);
    }

    private void stubPaths() {
        lenient().when(payment.get("memberId")).thenReturn(paymentMemberIdPath);
        lenient().when(payment.get("voidedAt")).thenReturn(voidedAtPath);
        lenient().when(payment.<LocalDate>get("periodCovered")).thenReturn(periodCoveredPath);
        lenient().when(payment.get("paymentMethod")).thenReturn(paymentMethodPath);
        lenient().when(query.subquery(Long.class)).thenReturn(clubMemberIds);
        lenient().when(clubMemberIds.from(Member.class)).thenReturn(member);
        lenient().when(member.get("clubId")).thenReturn(memberClubIdPath);
        lenient().when(paymentMemberIdPath.in(clubMemberIds)).thenReturn(belongsToClub);
    }

    private void stubPredicates() {
        lenient().when(builder.isNull(voidedAtPath)).thenReturn(notVoided);
        lenient().when(builder.equal(paymentMemberIdPath, MEMBER_ID)).thenReturn(matchesMember);
        lenient().when(builder.greaterThanOrEqualTo(periodCoveredPath, FROM)).thenReturn(fromPeriod);
        lenient().when(builder.lessThanOrEqualTo(periodCoveredPath, TO)).thenReturn(toPeriod);
        lenient().when(builder.equal(paymentMethodPath, PaymentMethod.CASH)).thenReturn(matchesMethod);
        lenient().when(builder.equal(paymentMethodPath, PaymentMethod.TRANSFER)).thenReturn(matchesMethod);
        lenient().when(builder.and(any(Predicate[].class))).thenReturn(combined);
    }
}
