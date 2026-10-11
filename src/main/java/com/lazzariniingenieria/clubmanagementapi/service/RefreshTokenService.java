package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.entity.RefreshToken;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
import com.lazzariniingenieria.clubmanagementapi.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                @Value("${jwt.refresh-expiration-ms}") long expirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
    }

    public String issueToken(Long userAccountId) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();
        RefreshToken refreshToken = RefreshToken.builder()
                .userAccountId(userAccountId)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plusMillis(expirationMs))
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Issued refresh token for userAccountId={}", userAccountId);

        return rawToken;
    }

    public void revokeActiveTokens(Long userAccountId) {
        int revokedCount = refreshTokenRepository.revokeActiveTokens(userAccountId, Instant.now());
        log.info("Revoked {} active refresh token(s) for userAccountId={}", revokedCount, userAccountId);
    }

    public RefreshToken consumeToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        validateUsable(refreshToken);
        refreshToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);
        log.info("Rotated refresh token for userAccountId={}", refreshToken.getUserAccountId());

        return refreshToken;
    }

    private void validateUsable(RefreshToken refreshToken) {
        boolean alreadyRevoked = refreshToken.getRevokedAt() != null;
        boolean expired = refreshToken.getExpiresAt().isBefore(Instant.now());

        if (alreadyRevoked || expired) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
