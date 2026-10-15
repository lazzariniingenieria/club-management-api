package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.RefreshToken;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
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

    private static final Long CLUB_ID = 1L;
    private static final String DNI = "30111222";
    private static final String RAW_PASSWORD = "s3cr3t123";
    private static final String HASHED_PASSWORD = "hashed-password";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userAccountRepository, passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    void shouldReturnTokenUserAccountIdRoleAndMemberIdWhenCredentialsAreValid() {
        UserAccount user = memberUser();
        LoginRequest request = new LoginRequest(CLUB_ID, DNI, RAW_PASSWORD);

        when(userAccountRepository.findByClubIdAndDni(CLUB_ID, DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3_600L);
        when(refreshTokenService.issueToken(1L)).thenReturn("raw-refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
        assertThat(response.userAccountId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(UserRole.MEMBER);
        assertThat(response.memberId()).isEqualTo(7L);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenClubAndDniCombinationDoesNotExist() {
        LoginRequest request = new LoginRequest(CLUB_ID, DNI, RAW_PASSWORD);
        when(userAccountRepository.findByClubIdAndDni(CLUB_ID, DNI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenPasswordDoesNotMatch() {
        UserAccount user = memberUser();
        LoginRequest request = new LoginRequest(CLUB_ID, DNI, "wrong-password");

        when(userAccountRepository.findByClubIdAndDni(CLUB_ID, DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", HASHED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenSameDniBelongsToAnotherClub() {
        LoginRequest request = new LoginRequest(99L, DNI, RAW_PASSWORD);
        when(userAccountRepository.findByClubIdAndDni(99L, DNI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenAccountIsInactive() {
        UserAccount user = memberUser();
        user.setActive(false);
        LoginRequest request = new LoginRequest(CLUB_ID, DNI, RAW_PASSWORD);

        when(userAccountRepository.findByClubIdAndDni(CLUB_ID, DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldReturnNewAccessAndRefreshTokenWhenRefreshTokenIsValid() {
        UserAccount user = memberUser();
        RefreshRequest request = new RefreshRequest("raw-refresh-token");
        RefreshToken consumedToken = RefreshToken.builder().userAccountId(1L).build();

        when(refreshTokenService.consumeToken("raw-refresh-token")).thenReturn(consumedToken);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3_600L);
        when(refreshTokenService.issueToken(1L)).thenReturn("new-refresh-token");

        RefreshResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenUnderlyingUserAccountNoLongerExists() {
        RefreshRequest request = new RefreshRequest("raw-refresh-token");
        RefreshToken consumedToken = RefreshToken.builder().userAccountId(1L).build();

        when(refreshTokenService.consumeToken("raw-refresh-token")).thenReturn(consumedToken);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenUserAccountIsInactive() {
        UserAccount user = memberUser();
        user.setActive(false);
        RefreshRequest request = new RefreshRequest("raw-refresh-token");
        RefreshToken consumedToken = RefreshToken.builder().userAccountId(1L).build();

        when(refreshTokenService.consumeToken("raw-refresh-token")).thenReturn(consumedToken);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtService);
    }

    private UserAccount memberUser() {
        return UserAccount.builder()
                .id(1L)
                .clubId(CLUB_ID)
                .memberId(7L)
                .dni(DNI)
                .passwordHash(HASHED_PASSWORD)
                .role(UserRole.MEMBER)
                .build();
    }
}
