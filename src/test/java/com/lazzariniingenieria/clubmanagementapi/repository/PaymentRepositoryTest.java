package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

// No database runs in the test suite, so these tests pin the JPQL contract the money rules depend on: dropping one of
// these clauses would still compile and pass every mocked service test while silently changing who is in delinquency.
class PaymentRepositoryTest {

    @Test
    void shouldExcludeVoidedPaymentsFromLastPaidPeriodUsedByDelinquency() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findLastPaidPeriodByMemberIds", Collection.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("p.voidedAt is null");
    }

    @Test
    void shouldScopePaymentLookupForVoidToTheClubOfTheToken() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("p.memberId in (select m.id from Member m where m.clubId = :clubId)");
    }

    @Test
    void shouldLockPaymentRowWhileVoidingSoConcurrentVoidsCannotBothSucceed() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        LockModeType lockMode = method.getAnnotation(Lock.class).value();

        assertThat(lockMode).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
