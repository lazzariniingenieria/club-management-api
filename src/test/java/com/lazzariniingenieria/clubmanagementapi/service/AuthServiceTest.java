package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequestDto;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponseDto;
import com.lazzariniingenieria.clubmanagementapi.dto.UserSummaryDto;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AccountDisabledException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.mapper.UserAccountMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DNI = "30111222";
    private static final String RAW_PASSWORD = "s3cr3t";
    private static final String HASHED_PASSWORD = "hashed-password";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserAccountMapper userAccountMapper;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userAccountRepository, passwordEncoder, jwtService, userAccountMapper);
    }

    @Test
    void shouldReturnTokenAndUserSummaryWhenCredentialsAreValid() {
        UserAccount user = activeUser();
        LoginRequestDto request = new LoginRequestDto(DNI, RAW_PASSWORD);
        UserSummaryDto summary = new UserSummaryDto(1L, DNI, UserRole.MEMBER, null, 1L);

        when(userAccountRepository.findByDni(DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(userAccountMapper.toSummaryDto(user)).thenReturn(summary);

        LoginResponseDto response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.user()).isEqualTo(summary);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenDniDoesNotExist() {
        LoginRequestDto request = new LoginRequestDto(DNI, RAW_PASSWORD);
        when(userAccountRepository.findByDni(DNI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenPasswordDoesNotMatch() {
        UserAccount user = activeUser();
        LoginRequestDto request = new LoginRequestDto(DNI, "wrong-password");

        when(userAccountRepository.findByDni(DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", HASHED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowAccountDisabledWhenUserIsInactive() {
        UserAccount user = activeUser();
        user.setActive(false);
        LoginRequestDto request = new LoginRequestDto(DNI, RAW_PASSWORD);

        when(userAccountRepository.findByDni(DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AccountDisabledException.class);

        verifyNoInteractions(jwtService);
    }

    private UserAccount activeUser() {
        return UserAccount.builder()
                .id(1L)
                .dni(DNI)
                .password(HASHED_PASSWORD)
                .role(UserRole.MEMBER)
                .clubId(1L)
                .active(true)
                .build();
    }
}
