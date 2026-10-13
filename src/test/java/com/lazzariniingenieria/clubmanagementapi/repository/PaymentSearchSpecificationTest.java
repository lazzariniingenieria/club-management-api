package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private Path<Long> memberIdPath;

    @Mock
    private Path<Object> memberClubIdPath;

    @Mock
    private Predicate combined;

    @BeforeEach
    void setUp() {
        Map<String, Path<?>> paymentPaths = Map.of("memberId", paymentMemberIdPath, "voidedAt", voidedAtPath, "periodCovered",
                periodCoveredPath, "paymentMethod", paymentMethodPath);
        Map<String, Path<?>> memberPaths = Map.of("id", memberIdPath, "clubId", memberClubIdPath);
        when(payment.get(anyString())).thenAnswer(invocation -> paymentPaths.get(invocation.getArgument(0)));
        when(member.get(anyString())).thenAnswer(invocation -> memberPaths.get(invocation.getArgument(0)));
        when(query.subquery(Long.class)).thenReturn(clubMemberIds);
        when(clubMemberIds.from(Member.class)).thenReturn(member);
        when(builder.and(any(Predicate[].class))).thenReturn(combined);
    }

    @Test
    void shouldScopeToClubAndExcludeVoidedPaymentsWhenNoFilterIsGiven() {
        Predicate result = toPredicate(new PaymentSearchRequest(null, null, null, null, false));

        verify(clubMemberIds).select(memberIdPath);
        verify(builder).equal(memberClubIdPath, CLUB_ID);
        verify(paymentMemberIdPath).in(clubMemberIds);
        verify(builder).isNull(voidedAtPath);
        assertThat(combinedPredicateCount()).isEqualTo(2);
        assertThat(result).isSameAs(combined);
    }

    @Test
    void shouldTreatMissingIncludeVoidedAsExcludingVoidedPayments() {
        toPredicate(new PaymentSearchRequest(null, null, null, null, null));

        verify(builder).isNull(voidedAtPath);
    }

    @Test
    void shouldKeepVoidedPaymentsWhenIncludeVoidedIsRequested() {
        toPredicate(new PaymentSearchRequest(null, null, null, null, true));

        verify(builder, never()).isNull(any());
        assertThat(combinedPredicateCount()).isEqualTo(1);
    }

    @Test
    void shouldCombineEveryFilterWhenAllAreGiven() {
        toPredicate(new PaymentSearchRequest(MEMBER_ID, FROM, TO, PaymentMethod.CASH, false));

        verify(builder).equal(paymentMemberIdPath, MEMBER_ID);
        verify(builder).greaterThanOrEqualTo(periodCoveredPath, FROM);
        verify(builder).lessThanOrEqualTo(periodCoveredPath, TO);
        verify(builder).equal(paymentMethodPath, PaymentMethod.CASH);
        assertThat(combinedPredicateCount()).isEqualTo(6);
    }

    @Test
    void shouldCombineMemberAndDateRangeWithoutFilteringByMethod() {
        toPredicate(new PaymentSearchRequest(MEMBER_ID, FROM, TO, null, false));

        verify(builder, never()).equal(paymentMethodPath, PaymentMethod.CASH);
        verify(builder, never()).equal(paymentMethodPath, PaymentMethod.TRANSFER);
        assertThat(combinedPredicateCount()).isEqualTo(5);
    }

    @Test
    void shouldApplyOnlyTheLowerPeriodBoundWhenToIsMissing() {
        toPredicate(new PaymentSearchRequest(null, FROM, null, null, false));

        verify(builder).greaterThanOrEqualTo(periodCoveredPath, FROM);
        verify(builder, never()).lessThanOrEqualTo(any(), any(LocalDate.class));
        assertThat(combinedPredicateCount()).isEqualTo(3);
    }

    @Test
    void shouldCombineMethodWithVoidedPaymentsWhenBothAreRequested() {
        toPredicate(new PaymentSearchRequest(null, null, TO, PaymentMethod.TRANSFER, true));

        verify(builder).lessThanOrEqualTo(periodCoveredPath, TO);
        verify(builder).equal(paymentMethodPath, PaymentMethod.TRANSFER);
        assertThat(combinedPredicateCount()).isEqualTo(3);
    }

    private Predicate toPredicate(PaymentSearchRequest filter) {
        PaymentSearchSpecification specification = new PaymentSearchSpecification(CLUB_ID, filter);

        return specification.toPredicate(payment, query, builder);
    }

    private int combinedPredicateCount() {
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicatesCaptor.capture());

        return predicatesCaptor.getValue().length;
    }
}
