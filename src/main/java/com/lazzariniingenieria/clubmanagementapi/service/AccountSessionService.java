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

        return !issuedBeforePasswordChange(accessToken.issuedAt(), account.get().getPasswordChangedAt());
    }

    // The JWT "iat" claim only keeps whole seconds, so the change instant is truncated the same way; otherwise a token
    // issued a few milliseconds after the change, within the same second, would be rejected as if it were older.
    private boolean issuedBeforePasswordChange(Instant issuedAt, Instant passwordChangedAt) {
        if (passwordChangedAt == null) {
            return false;
        }

        Instant passwordChangedAtSecond = passwordChangedAt.truncatedTo(ChronoUnit.SECONDS);

        return issuedAt.isBefore(passwordChangedAtSecond);
    }
}
