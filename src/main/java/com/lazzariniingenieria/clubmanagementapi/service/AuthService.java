package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.RefreshToken;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private TokenPair issueTokens(UserAccount user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issueToken(user.getId());

        return new TokenPair(accessToken, refreshToken, jwtService.getExpirationSeconds());
    }

    private InvalidCredentialsException loginRejected(LoginRequest request) {
        log.warn("Login rejected for clubId={}, dni={}", request.clubId(), request.dni());

        return new InvalidCredentialsException();
    }

    private record TokenPair(String accessToken, String refreshToken, Long expiresIn) {
    }
}
