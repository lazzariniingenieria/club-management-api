package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AccessTokenClaims;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountSessionServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long USER_ACCOUNT_ID = 10L;
    private static final Instant ISSUED_AT = Instant.parse("2026-10-24T12:00:00Z");
    private static final AccessTokenClaims ACCESS_TOKEN =
            new AccessTokenClaims(new AuthenticatedUser(USER_ACCOUNT_ID, CLUB_ID, UserRole.ADMIN, null), ISSUED_AT);

    @Mock
    private UserAccountRepository userAccountRepository;

    private AccountSessionService accountSessionService;

    @BeforeEach
    void setUp() {
        accountSessionService = new AccountSessionService(userAccountRepository);
    }

    @Test
    void shouldAcceptSessionOfAnActiveAccountWhosePasswordWasNeverChanged() {
        stubAccount(account(true, null));

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isTrue();
    }

    @Test
    void shouldRejectSessionWhenAccountWasDeactivated() {
        stubAccount(account(false, null));

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isFalse();
    }

    @Test
    void shouldRejectSessionWhenAccountNoLongerExistsInTheClubOfTheToken() {
        when(userAccountRepository.findByIdAndClubId(USER_ACCOUNT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isFalse();
    }

    @Test
    void shouldRejectTokenIssuedBeforeTheSessionsWereRevoked() {
        stubAccount(account(true, Instant.parse("2026-10-24T12:00:01Z")));

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isFalse();
    }

    @Test
    void shouldAcceptTokenIssuedAfterTheSessionsWereRevoked() {
        stubAccount(account(true, Instant.parse("2026-10-24T11:59:59Z")));

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isTrue();
    }

    @Test
    void shouldAcceptTokenIssuedWithinTheSameSecondAsTheSessionsRevocationBecauseIssuedAtHasNoMilliseconds() {
        stubAccount(account(true, Instant.parse("2026-10-24T12:00:00.750Z")));

        assertThat(accountSessionService.isSessionValid(ACCESS_TOKEN)).isTrue();
    }

    private void stubAccount(UserAccount account) {
        when(userAccountRepository.findByIdAndClubId(USER_ACCOUNT_ID, CLUB_ID)).thenReturn(Optional.of(account));
    }

    private UserAccount account(boolean active, Instant sessionsRevokedAt) {
        return UserAccount.builder()
                .id(USER_ACCOUNT_ID)
                .clubId(CLUB_ID)
                .role(UserRole.ADMIN)
                .active(active)
                .sessionsRevokedAt(sessionsRevokedAt)
                .build();
    }
}
