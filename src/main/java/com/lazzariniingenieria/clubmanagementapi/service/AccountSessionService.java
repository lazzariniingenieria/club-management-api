package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AccessTokenClaims;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountSessionService {

    private final UserAccountRepository userAccountRepository;

    public boolean isSessionValid(AccessTokenClaims accessToken) {
        AuthenticatedUser authenticatedUser = accessToken.authenticatedUser();
        Optional<UserAccount> account = userAccountRepository.findByIdAndClubId(authenticatedUser.userAccountId(),
                authenticatedUser.clubId());

        if (account.isEmpty() || !account.get().isActive()) {
            return false;
        }

        return !issuedBeforeSessionsWereRevoked(accessToken.issuedAt(), account.get().getSessionsRevokedAt());
    }

    // The JWT "iat" claim only keeps whole seconds, so a token issued within the same second as the revocation can't be told
    // apart from one issued just before it. Every token of that second is rejected, even one issued right after the revocation;
    // a refresh made once that second has passed issues a token that is accepted.
    private boolean issuedBeforeSessionsWereRevoked(Instant issuedAt, Instant sessionsRevokedAt) {
        if (sessionsRevokedAt == null) {
            return false;
        }

        Instant firstSecondAfterRevocation = sessionsRevokedAt.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        return issuedAt.isBefore(firstSecondAfterRevocation);
    }
}
