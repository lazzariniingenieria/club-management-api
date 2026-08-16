package com.lazzariniingenieria.clubmanagementapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L);
    }

    @Test
    void shouldGenerateTokenAndExtractDniFromIt() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .dni("30111222")
                .role(UserRole.MEMBER)
                .clubId(1L)
                .active(true)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractDni(token)).isEqualTo("30111222");
    }

    @Test
    void shouldRejectTamperedToken() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .dni("30111222")
                .role(UserRole.MEMBER)
                .clubId(1L)
                .active(true)
                .build();

        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .dni("30111222")
                .role(UserRole.MEMBER)
                .clubId(1L)
                .active(true)
                .build();

        JwtService otherJwtService = new JwtService("a-completely-different-secret-key-32-bytes-min", 3_600_000L);
        String token = otherJwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldExposeConfiguredExpirationInSeconds() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(3600L);
    }
}
