package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberAccountRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberAccountResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ResetMemberAccountPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidMemberAccountException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberAccountNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberAlreadyHasAccountException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.MemberAccountService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MemberAccountController.class)
@Import(SecurityConfig.class)
class MemberAccountControllerTest {

    private static final String FIXTURES_PATH = "fixtures/member-account/";
    private static final String ACCOUNT_PATH = "/api/members/{memberId}/account";
    private static final String PASSWORD_PATH = "/api/members/{memberId}/account/password";
    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 34L;
    private static final Long ACCOUNT_ID = 40L;
    private static final Long ACTING_USER_ID = 12L;
    private static final String DNI = "30111222";
    private static final Instant CREATED_AT = Instant.parse("2026-11-18T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnAccountWhenMemberHasOne() throws Exception {
        when(memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).thenReturn(Optional.of(memberAccountResponse()));

        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(40)))
                .andExpect(jsonPath("$.memberId", is(34)))
                .andExpect(jsonPath("$.dni", is(DNI)))
                .andExpect(jsonPath("$.role", is("MEMBER")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.createdAt", is("2026-11-18T15:00:00Z")))
                .andExpect(jsonPath("$.createdByUserId", is(12)))
                .andExpect(jsonPath("$.updatedByUserId", is(12)));
    }

    @Test
    void shouldNeverExposeCredentialsOrSessionDataOfTheAccount() throws Exception {
        when(memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).thenReturn(Optional.of(memberAccountResponse()));

        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.sessionsRevokedAt").doesNotExist());
    }

    @Test
    void shouldReturnAccountWhenRequesterIsSuperAdmin() throws Exception {
        when(memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).thenReturn(Optional.of(memberAccountResponse()));

        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(40)));
    }

    @Test
    void shouldReturnNoContentWhenMemberHasNoAccount() throws Exception {
        when(memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asAdmin()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void shouldReturnNotFoundWhenGettingAccountOfMemberFromAnotherClub() throws Exception {
        when(memberAccountService.getAccount(CLUB_ID, MEMBER_ID)).thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Member 34 not found")));
    }

    @Test
    void shouldReturnForbiddenWithoutBodyWhenMemberRequestsAnAccount() throws Exception {
        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID).with(asMember()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        verify(memberAccountService, never()).getAccount(any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenRequestingAccountWithoutAuthentication() throws Exception {
        mockMvc.perform(get(ACCOUNT_PATH, MEMBER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void shouldReturnCreatedAccountWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(40)))
                .andExpect(jsonPath("$.role", is("MEMBER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldPassTheRequestedPasswordToTheServiceWhenCreatingAccount() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        CreateMemberAccountRequest expectedRequest = new CreateMemberAccountRequest("s3cr3t123");
        verify(memberAccountService).createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), eq(expectedRequest));
    }

    @Test
    void shouldCreateAccountWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsBlank() throws Exception {
        String requestBody = readFixture("create-member-account-request-blank-password.json");

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.details", hasItem("password: password is required")));

        verify(memberAccountService, never()).createAccount(any(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsTooShort() throws Exception {
        String requestBody = readFixture("create-member-account-request-short-password.json");

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("password: password must be at least 8 characters")));
    }

    @Test
    void shouldReturnBadRequestInsteadOfFailingWhenPasswordExceedsTheBcryptByteLimit() throws Exception {
        String requestBody = readFixture("create-member-account-request-password-too-many-bytes.json");

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("password: password must be at most 72 bytes (accents count as 2)")));

        verify(memberAccountService, never()).createAccount(any(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenMemberAppIsNotEnabled() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenThrow(new InvalidMemberAccountException("member app is not enabled for this club"));

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("member app is not enabled for this club")));
    }

    @Test
    void shouldReturnNotFoundWhenCreatingAccountForMemberFromAnotherClub() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Member 34 not found")));
    }

    @Test
    void shouldReturnConflictWhenMemberAlreadyHasAnAccount() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenThrow(new MemberAlreadyHasAccountException(MEMBER_ID));

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("member 34 already has an account")));
    }

    @Test
    void shouldReturnConflictWhenAnotherAccountUsesTheMemberDni() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenThrow(new DuplicateDniException(DNI));

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("dni 30111222 is already in use in this club")));
    }

    @Test
    void shouldReturnGenericConflictWhenTwoSimultaneousCreationsHitTheDatabaseConstraint() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");
        when(memberAccountService.createAccount(any(AuthenticatedUser.class), eq(MEMBER_ID), any(CreateMemberAccountRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_account_member_id"));

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Request violates a database constraint")));
    }

    @Test
    void shouldReturnForbiddenWithoutBodyWhenMemberCreatesAnAccount() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        verify(memberAccountService, never()).createAccount(any(), any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenCreatingAccountWithoutAuthentication() throws Exception {
        String requestBody = readFixture("create-member-account-request-valid.json");

        mockMvc.perform(post(ACCOUNT_PATH, MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldResetPasswordWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");
        when(memberAccountService.resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID), any(ResetMemberAccountPasswordRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(40)))
                .andExpect(jsonPath("$.newPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldPassTheNewPasswordToTheServiceWhenResetting() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");
        when(memberAccountService.resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID), any(ResetMemberAccountPasswordRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(memberAccountService).resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID),
                eq(new ResetMemberAccountPasswordRequest("n3wS3cr3t")));
    }

    @Test
    void shouldResetPasswordWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");
        when(memberAccountService.resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID), any(ResetMemberAccountPasswordRequest.class)))
                .thenReturn(memberAccountResponse());

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBadRequestWhenNewPasswordIsBlank() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-blank.json");

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("newPassword: newPassword is required")));

        verify(memberAccountService, never()).resetPassword(any(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenNewPasswordIsTooShort() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-too-short.json");

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("newPassword: newPassword must be at least 8 characters")));
    }

    @Test
    void shouldReturnBadRequestInsteadOfFailingWhenNewPasswordExceedsTheBcryptByteLimit() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-too-many-bytes.json");

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("newPassword: newPassword must be at most 72 bytes (accents count as 2)")));

        verify(memberAccountService, never()).resetPassword(any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundWhenResettingPasswordOfMemberFromAnotherClub() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");
        when(memberAccountService.resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID), any(ResetMemberAccountPasswordRequest.class)))
                .thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Member 34 not found")));
    }

    @Test
    void shouldReturnNotFoundWhenMemberHasNoMemberAccount() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");
        when(memberAccountService.resetPassword(any(AuthenticatedUser.class), eq(MEMBER_ID), any(ResetMemberAccountPasswordRequest.class)))
                .thenThrow(new MemberAccountNotFoundException(MEMBER_ID));

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("member 34 has no member account")));
    }

    @Test
    void shouldReturnForbiddenWithoutBodyWhenMemberResetsAPassword() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        verify(memberAccountService, never()).resetPassword(any(), any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenResettingPasswordWithoutAuthentication() throws Exception {
        String requestBody = readFixture("reset-member-account-password-request-valid.json");

        mockMvc.perform(patch(PASSWORD_PATH, MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    private MemberAccountResponse memberAccountResponse() {
        return new MemberAccountResponse(ACCOUNT_ID, MEMBER_ID, DNI, UserRole.MEMBER, true, CREATED_AT, CREATED_AT, ACTING_USER_ID,
                ACTING_USER_ID);
    }

    private RequestPostProcessor asSuperAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    private RequestPostProcessor asAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private RequestPostProcessor asMember() {
        AuthenticatedUser principal = new AuthenticatedUser(ACCOUNT_ID, CLUB_ID, UserRole.MEMBER, MEMBER_ID);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private String readFixture(String fileName) throws IOException {
        String resourcePath = FIXTURES_PATH + fileName;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            byte[] fileBytes = inputStream.readAllBytes();

            return new String(fileBytes, StandardCharsets.UTF_8);
        }
    }
}
