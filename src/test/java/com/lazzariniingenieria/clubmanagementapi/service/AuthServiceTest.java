package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userAccountRepository, passwordEncoder, jwtService);
    }

    @Test
    void shouldReturnTokenUserAccountIdRoleAndMemberIdWhenCredentialsAreValid() {
        UserAccount user = memberUser();
        LoginRequest request = new LoginRequest(CLUB_ID, DNI, RAW_PASSWORD);

        when(userAccountRepository.findByClubIdAndDni(CLUB_ID, DNI)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("signed-token");
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
