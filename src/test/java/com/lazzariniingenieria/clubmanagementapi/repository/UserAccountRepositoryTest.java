package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class UserAccountRepositoryTest {

    @Test
    void shouldLockTheAccountWhileLoggingInSoASessionCutCannotMissTheNewTokens() throws NoSuchMethodException {
        Method method = UserAccountRepository.class.getMethod("findByClubIdAndDniForUpdate", Long.class, String.class);

        assertThat(lockModeOf(method)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldLockTheAccountWhileRefreshingSoASessionCutCannotMissTheRotatedTokens() throws NoSuchMethodException {
        Method method = UserAccountRepository.class.getMethod("findByIdForUpdate", Long.class);

        assertThat(lockModeOf(method)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldLockTheAccountWhileChangingTheOwnPassword() throws NoSuchMethodException {
        Method method = UserAccountRepository.class.getMethod("findByIdAndClubIdForUpdate", Long.class, Long.class);

        assertThat(lockModeOf(method)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldLockTheAdminWhileDeactivatingItOrResettingItsPassword() throws NoSuchMethodException {
        Method method = UserAccountRepository.class.getMethod("findByIdAndClubIdAndRoleForUpdate", Long.class, Long.class, UserRole.class);

        assertThat(lockModeOf(method)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private LockModeType lockModeOf(Method method) {
        return method.getAnnotation(Lock.class).value();
    }
}
