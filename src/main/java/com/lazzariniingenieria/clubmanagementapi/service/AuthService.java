package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.ChangeOwnPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.RefreshToken;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginResponse login(LoginRequest request) {
        Optional<UserAccount> userAccount = userAccountRepository.findByClubIdAndDni(request.clubId(), request.dni());

        if (userAccount.isEmpty()) {
            throw loginRejected(request);
        }

        UserAccount user = userAccount.get();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches) {
            throw loginRejected(request);
        }

        if (!user.isActive()) {
            throw loginRejected(request);
        }

        TokenPair tokens = issueTokens(user);

        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), user.getId(),
                user.getRole(), user.getMemberId());
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        RefreshToken consumedToken = refreshTokenService.consumeToken(request.refreshToken());
        UserAccount user = userAccountRepository.findById(consumedToken.getUserAccountId())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        TokenPair tokens = issueTokens(user);

        return new RefreshResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    @Transactional
    public void changeOwnPassword(AuthenticatedUser currentUser, ChangeOwnPasswordRequest request) {
        Long userAccountId = currentUser.userAccountId();
        Long clubId = currentUser.clubId();
        UserAccount account = findAuthenticatedAccountOrThrow(userAccountId, clubId);
        Instant now = Instant.now();
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setPasswordChangedAt(now);
        account.setUpdatedAt(now);
        account.setUpdatedByUserId(userAccountId);

        userAccountRepository.save(account);
        refreshTokenService.revokeActiveTokens(userAccountId);
        log.info("userAccountId={} changed its own password in clubId={}", userAccountId, clubId);
    }

    private TokenPair issueTokens(UserAccount user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issueToken(user.getId());

        return new TokenPair(accessToken, refreshToken, jwtService.getExpirationSeconds());
    }

    private UserAccount findAuthenticatedAccountOrThrow(Long userAccountId, Long clubId) {
        String missingAccountMessage = "Authenticated userAccountId=" + userAccountId + " has no account in clubId=" + clubId;

        return userAccountRepository
                .findByIdAndClubId(userAccountId, clubId)
                .orElseThrow(() -> new IllegalStateException(missingAccountMessage));
    }

    private InvalidCredentialsException loginRejected(LoginRequest request) {
        log.warn("Login rejected for clubId={}, dni={}", request.clubId(), request.dni());

        return new InvalidCredentialsException();
    }

    private record TokenPair(String accessToken, String refreshToken, Long expiresIn) {
    }
}
