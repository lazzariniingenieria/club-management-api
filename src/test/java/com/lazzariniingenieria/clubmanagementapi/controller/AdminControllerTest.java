package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AdminNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AdminService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    private static final String FIXTURES_PATH = "fixtures/admin/";
    private static final Long CLUB_ID = 1L;
    private static final Long ADMIN_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnCreatedAdminWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-admin-request-valid.json");
        AdminResponse response = adminResponse();
        when(adminService.createAdmin(any(AuthenticatedUser.class), any(CreateAdminRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admins")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.dni", is("30222333")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void shouldReturnConflictWhenCreatingAdminWithDuplicateDni() throws Exception {
        String requestBody = readFixture("create-admin-request-valid.json");
        when(adminService.createAdmin(any(AuthenticatedUser.class), any(CreateAdminRequest.class)))
                .thenThrow(new DuplicateDniException("30222333"));

        mockMvc.perform(post("/api/admins")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenDniIsBlank() throws Exception {
        String requestBody = readFixture("create-admin-request-blank-dni.json");

        mockMvc.perform(post("/api/admins")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsTooShort() throws Exception {
        String requestBody = readFixture("create-admin-request-short-password.json");

        mockMvc.perform(post("/api/admins")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenEmailFormatIsInvalid() throws Exception {
        String requestBody = readFixture("create-admin-request-invalid-email.json");

        mockMvc.perform(post("/api/admins")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("email: email must be a valid address")));
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsNotSuperAdmin() throws Exception {
        String requestBody = readFixture("create-admin-request-valid.json");

        mockMvc.perform(post("/api/admins")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        String requestBody = readFixture("create-admin-request-valid.json");

        mockMvc.perform(post("/api/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnListOfAdmins() throws Exception {
        when(adminService.listAdmins(CLUB_ID)).thenReturn(List.of(adminResponse()));

        mockMvc.perform(get("/api/admins").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(10)));
    }

    @Test
    void shouldReturnAdminByIdWhenFound() throws Exception {
        when(adminService.getAdmin(CLUB_ID, ADMIN_ID)).thenReturn(adminResponse());

        mockMvc.perform(get("/api/admins/{adminId}", ADMIN_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni", is("30222333")));
    }

    @Test
    void shouldReturnNotFoundWhenAdminDoesNotExist() throws Exception {
        when(adminService.getAdmin(CLUB_ID, ADMIN_ID)).thenThrow(new AdminNotFoundException(ADMIN_ID));

        mockMvc.perform(get("/api/admins/{adminId}", ADMIN_ID).with(asSuperAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateAdminWhenValid() throws Exception {
        String requestBody = readFixture("update-admin-request-valid.json");
        when(adminService.updateAdmin(any(AuthenticatedUser.class), eq(ADMIN_ID), any(UpdateAdminRequest.class)))
                .thenReturn(adminResponse());

        mockMvc.perform(patch("/api/admins/{adminId}", ADMIN_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingWithBlankDni() throws Exception {
        String requestBody = readFixture("update-admin-request-blank-dni.json");

        mockMvc.perform(patch("/api/admins/{adminId}", ADMIN_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeactivateAdmin() throws Exception {
        AdminResponse deactivated = new AdminResponse(ADMIN_ID, "30222333", "admin@example.com", 5L, false,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), 1L, 1L);
        when(adminService.deactivateAdmin(any(AuthenticatedUser.class), eq(ADMIN_ID))).thenReturn(deactivated);

        mockMvc.perform(patch("/api/admins/{adminId}/deactivate", ADMIN_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void shouldReactivateAdmin() throws Exception {
        when(adminService.reactivateAdmin(any(AuthenticatedUser.class), eq(ADMIN_ID))).thenReturn(adminResponse());

        mockMvc.perform(patch("/api/admins/{adminId}/reactivate", ADMIN_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    private AdminResponse adminResponse() {
        return new AdminResponse(ADMIN_ID, "30222333", "admin@example.com", 5L, true,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), 1L, 1L);
    }

    private RequestPostProcessor asSuperAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    private RequestPostProcessor asAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(2L, CLUB_ID, UserRole.ADMIN, 7L);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String readFixture(String fileName) throws IOException {
        String resourcePath = FIXTURES_PATH + fileName;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            byte[] fileBytes = inputStream.readAllBytes();

            return new String(fileBytes, StandardCharsets.UTF_8);
        }
    }
}
