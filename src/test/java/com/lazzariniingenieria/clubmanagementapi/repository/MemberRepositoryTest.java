package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

class MemberRepositoryTest {

    @Test
    void shouldLockTheMemberRowSoItsBookingsAndDeactivationRunOneAtATime() throws NoSuchMethodException {
        Method method = MemberRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        LockModeType lockMode = method.getAnnotation(Lock.class).value();

        assertThat(lockMode).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldScopeTheLockedMemberToTheClubOfTheToken() throws NoSuchMethodException {
        Method method = MemberRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("m.id = :id and m.clubId = :clubId");
    }
}
