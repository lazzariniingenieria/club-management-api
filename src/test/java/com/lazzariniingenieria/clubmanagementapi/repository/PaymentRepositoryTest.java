package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
    void shouldScopeFeeReceiptPaymentsToTheClubOfTheToken() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findActiveByClubIdAndPaidAtInRange", Long.class, Instant.class, Instant.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("p.memberId in (select m.id from Member m where m.clubId = :clubId)");
    }

    @Test
    void shouldExcludeVoidedPaymentsFromFeeReceipts() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findActiveByClubIdAndPaidAtInRange", Long.class, Instant.class, Instant.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("p.voidedAt is null");
    }

    @Test
    void shouldExcludeTheUpperBoundOfTheFeeReceiptCollectionRange() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findActiveByClubIdAndPaidAtInRange", Long.class, Instant.class, Instant.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("p.paidAt >= :paidFrom and p.paidAt < :paidUntil");
    }

    @Test
    void shouldLockPaymentRowWhileVoidingSoConcurrentVoidsCannotBothSucceed() throws NoSuchMethodException {
        Method method = PaymentRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        LockModeType lockMode = method.getAnnotation(Lock.class).value();

        assertThat(lockMode).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
