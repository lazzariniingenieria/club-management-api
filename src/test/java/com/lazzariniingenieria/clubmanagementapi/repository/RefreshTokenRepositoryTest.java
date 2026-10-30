package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class RefreshTokenRepositoryTest {

    @Test
    void shouldOnlyRevokeATokenThatIsStillActiveSoConcurrentRotationsCannotBothSucceed() throws NoSuchMethodException {
        Method method = RefreshTokenRepository.class.getMethod("revokeIfActive", Long.class, Instant.class);

        String jpql = method.getAnnotation(Query.class).value();

        assertThat(jpql).contains("t.revokedAt is null");
    }
}
