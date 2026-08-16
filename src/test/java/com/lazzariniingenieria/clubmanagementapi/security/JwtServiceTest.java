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
    void shouldGenerateTokenAndExtractAuthenticatedUserFromIt() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .clubId(10L)
                .memberId(5L)
                .nationalId("30111222")
                .role(UserRole.ADMIN)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        AuthenticatedUser authenticatedUser = jwtService.extractAuthenticatedUser(token);
        assertThat(authenticatedUser.userAccountId()).isEqualTo(1L);
        assertThat(authenticatedUser.clubId()).isEqualTo(10L);
        assertThat(authenticatedUser.role()).isEqualTo(UserRole.ADMIN);
        assertThat(authenticatedUser.memberId()).isEqualTo(5L);
    }

    @Test
    void shouldExtractNullMemberIdWhenUserHasNoLinkedMember() {
        UserAccount user = UserAccount.builder()
                .id(2L)
                .clubId(10L)
                .memberId(null)
                .nationalId("30111222")
                .role(UserRole.SUPER_ADMIN)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractAuthenticatedUser(token).memberId()).isNull();
    }

    @Test
    void shouldRejectTamperedToken() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .clubId(10L)
                .nationalId("30111222")
                .role(UserRole.MEMBER)
                .build();

        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentSecret() {
        UserAccount user = UserAccount.builder()
                .id(1L)
                .clubId(10L)
                .nationalId("30111222")
                .role(UserRole.MEMBER)
                .build();

        JwtService otherJwtService = new JwtService("a-completely-different-secret-key-32-bytes-min", 3_600_000L);
        String token = otherJwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
