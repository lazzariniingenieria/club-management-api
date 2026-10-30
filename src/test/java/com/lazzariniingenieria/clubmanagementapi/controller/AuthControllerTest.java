package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.ChangeOwnPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidRefreshTokenException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.AuthService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String FIXTURES_PATH = "fixtures/auth/";
    private static final Long CLUB_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnTokenUserAccountIdRoleAndMemberIdWhenCredentialsAreValid() throws Exception {
        String requestBody = readFixture("login-request-valid.json");
        LoginResponse response = new LoginResponse("token-123", "refresh-123", 3_600L, 1L, UserRole.MEMBER, 7L);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("token-123")))
                .andExpect(jsonPath("$.refreshToken", is("refresh-123")))
                .andExpect(jsonPath("$.expiresIn", is(3_600)))
                .andExpect(jsonPath("$.userAccountId", is(1)))
                .andExpect(jsonPath("$.role", is("MEMBER")))
                .andExpect(jsonPath("$.memberId", is(7)));
    }

    @Test
    void shouldReturnBadRequestWhenClubIdIsMissing() throws Exception {
        String requestBody = readFixture("login-request-missing-club-id.json");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenDniIsBlank() throws Exception {
        String requestBody = readFixture("login-request-blank-dni.json");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        String requestBody = readFixture("login-request-wrong-password.json");
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnNewAccessAndRefreshTokenWhenRefreshTokenIsValid() throws Exception {
        String requestBody = readFixture("refresh-request-valid.json");
        RefreshResponse response = new RefreshResponse("new-access-token", "new-refresh-token", 3_600L);
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("new-access-token")))
                .andExpect(jsonPath("$.refreshToken", is("new-refresh-token")))
                .andExpect(jsonPath("$.expiresIn", is(3_600)));
    }

    @Test
    void shouldReturnBadRequestWhenRefreshTokenIsBlank() throws Exception {
        String requestBody = readFixture("refresh-request-blank-token.json");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenRefreshTokenIsInvalid() throws Exception {
        String requestBody = readFixture("refresh-request-valid.json");
        when(authService.refresh(any(RefreshRequest.class))).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldChangeOwnPasswordAndReturnNoContentWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("change-own-password-request-valid.json");
        AuthenticatedUser admin = new AuthenticatedUser(2L, CLUB_ID, UserRole.ADMIN, 7L);

        mockMvc.perform(patch("/api/auth/me/password")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        verify(authService).changeOwnPassword(admin, new ChangeOwnPasswordRequest("brand-new-password"));
    }

    @Test
    void shouldChangeOwnPasswordWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("change-own-password-request-valid.json");
        AuthenticatedUser superAdmin = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

        mockMvc.perform(patch("/api/auth/me/password")
                        .with(authenticatedAs(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        verify(authService).changeOwnPassword(superAdmin, new ChangeOwnPasswordRequest("brand-new-password"));
    }

    @Test
    void shouldReturnBadRequestWhenOwnNewPasswordIsTooShort() throws Exception {
        String requestBody = readFixture("change-own-password-request-too-short.json");
        AuthenticatedUser admin = new AuthenticatedUser(2L, CLUB_ID, UserRole.ADMIN, 7L);

        mockMvc.perform(patch("/api/auth/me/password")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authService, never()).changeOwnPassword(any(), any());
    }

    @Test
    void shouldReturnBadRequestInsteadOfFailingWhenOwnNewPasswordExceedsTheBcryptByteLimit() throws Exception {
        String requestBody = readFixture("change-own-password-request-too-many-bytes.json");
        AuthenticatedUser admin = new AuthenticatedUser(2L, CLUB_ID, UserRole.ADMIN, 7L);

        mockMvc.perform(patch("/api/auth/me/password")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("newPassword: newPassword must be at most 72 bytes (accents count as 2)")));

        verify(authService, never()).changeOwnPassword(any(), any());
    }

    @Test
    void shouldReturnForbiddenWhenMemberChangesOwnPassword() throws Exception {
        String requestBody = readFixture("change-own-password-request-valid.json");
        AuthenticatedUser member = new AuthenticatedUser(3L, CLUB_ID, UserRole.MEMBER, 9L);

        mockMvc.perform(patch("/api/auth/me/password")
                        .with(authenticatedAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenChangingOwnPasswordWithoutAuthentication() throws Exception {
        String requestBody = readFixture("change-own-password-request-valid.json");

        mockMvc.perform(patch("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor authenticatedAs(AuthenticatedUser principal) {
        String roleAuthority = "ROLE_" + principal.role().name();

        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(roleAuthority))));
    }

    private String readFixture(String fileName) throws IOException {
        String resourcePath = FIXTURES_PATH + fileName;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            byte[] fileBytes = inputStream.readAllBytes();

            return new String(fileBytes, StandardCharsets.UTF_8);
        }
    }
}
