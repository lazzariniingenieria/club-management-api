package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.entity.RefreshToken;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
import com.lazzariniingenieria.clubmanagementapi.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long USER_ACCOUNT_ID = 1L;
    private static final long EXPIRATION_MS = 2_592_000_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, EXPIRATION_MS);
    }

    @Test
    void shouldPersistHashedTokenWithFutureExpirationAndReturnRawToken() {
        ArgumentCaptor<RefreshToken> savedTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.issueToken(USER_ACCOUNT_ID);

        verify(refreshTokenRepository).save(savedTokenCaptor.capture());
        RefreshToken savedToken = savedTokenCaptor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(savedToken.getUserAccountId()).isEqualTo(USER_ACCOUNT_ID);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.getRevokedAt()).isNull();
    }

    @Test
    void shouldGenerateDifferentRawTokensOnEachCall() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String firstToken = refreshTokenService.issueToken(USER_ACCOUNT_ID);
        String secondToken = refreshTokenService.issueToken(USER_ACCOUNT_ID);

        assertThat(firstToken).isNotEqualTo(secondToken);
    }

    @Test
    void shouldHashSameRawTokenToTheSameValue() {
        ArgumentCaptor<RefreshToken> savedTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String rawToken = refreshTokenService.issueToken(USER_ACCOUNT_ID);
        verify(refreshTokenRepository).save(savedTokenCaptor.capture());
        String storedHash = savedTokenCaptor.getValue().getTokenHash();
        RefreshToken storedRefreshToken = validRefreshToken(storedHash);
        when(refreshTokenRepository.findByTokenHash(storedHash)).thenReturn(Optional.of(storedRefreshToken));

        refreshTokenService.consumeToken(rawToken);

        assertThat(storedRefreshToken.getRevokedAt()).isNotNull();
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenDoesNotExist() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consumeToken("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenIsAlreadyRevoked() {
        RefreshToken revokedToken = validRefreshToken("hash");
        revokedToken.setRevokedAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.consumeToken("some-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenIsExpired() {
        RefreshToken expiredToken = validRefreshToken("hash");
        expiredToken.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.consumeToken("some-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private RefreshToken validRefreshToken(String tokenHash) {
        return RefreshToken.builder()
                .id(1L)
                .userAccountId(USER_ACCOUNT_ID)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(60))
                .createdAt(Instant.now())
                .build();
    }
}
