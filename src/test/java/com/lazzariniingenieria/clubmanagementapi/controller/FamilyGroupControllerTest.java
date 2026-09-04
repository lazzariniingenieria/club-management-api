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
import com.lazzariniingenieria.clubmanagementapi.dto.CreateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.FamilyGroupService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(FamilyGroupController.class)
@Import(SecurityConfig.class)
class FamilyGroupControllerTest {

    private static final String FIXTURES_PATH = "fixtures/family-group/";
    private static final Long CLUB_ID = 1L;
    private static final Long FAMILY_GROUP_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FamilyGroupService familyGroupService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnCreatedFamilyGroupWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-family-group-request-valid.json");
        when(familyGroupService.createFamilyGroup(eq(CLUB_ID), any(CreateFamilyGroupRequest.class)))
                .thenReturn(familyGroupResponse());

        mockMvc.perform(post("/api/family-groups")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Familia Gomez")));
    }

    @Test
    void shouldReturnCreatedFamilyGroupWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-family-group-request-valid.json");
        when(familyGroupService.createFamilyGroup(eq(CLUB_ID), any(CreateFamilyGroupRequest.class)))
                .thenReturn(familyGroupResponse());

        mockMvc.perform(post("/api/family-groups")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnBadRequestWhenNameIsTooLong() throws Exception {
        String requestBody = readFixture("create-family-group-request-name-too-long.json");

        mockMvc.perform(post("/api/family-groups")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        String requestBody = readFixture("create-family-group-request-valid.json");

        mockMvc.perform(post("/api/family-groups")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        String requestBody = readFixture("create-family-group-request-valid.json");

        mockMvc.perform(post("/api/family-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnListOfFamilyGroups() throws Exception {
        when(familyGroupService.listFamilyGroups(CLUB_ID)).thenReturn(List.of(familyGroupResponse()));

        mockMvc.perform(get("/api/family-groups").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)));
    }

    @Test
    void shouldReturnFamilyGroupByIdWhenFound() throws Exception {
        when(familyGroupService.getFamilyGroup(CLUB_ID, FAMILY_GROUP_ID)).thenReturn(familyGroupResponse());

        mockMvc.perform(get("/api/family-groups/{familyGroupId}", FAMILY_GROUP_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Familia Gomez")));
    }

    @Test
    void shouldReturnNotFoundWhenFamilyGroupDoesNotExist() throws Exception {
        when(familyGroupService.getFamilyGroup(CLUB_ID, FAMILY_GROUP_ID))
                .thenThrow(new FamilyGroupNotFoundException(FAMILY_GROUP_ID));

        mockMvc.perform(get("/api/family-groups/{familyGroupId}", FAMILY_GROUP_ID).with(asSuperAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateFamilyGroupWhenValid() throws Exception {
        String requestBody = readFixture("update-family-group-request-valid.json");
        when(familyGroupService.updateFamilyGroup(eq(CLUB_ID), eq(FAMILY_GROUP_ID), any(UpdateFamilyGroupRequest.class)))
                .thenReturn(familyGroupResponse());

        mockMvc.perform(patch("/api/family-groups/{familyGroupId}", FAMILY_GROUP_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingWithNameTooLong() throws Exception {
        String requestBody = readFixture("create-family-group-request-name-too-long.json");

        mockMvc.perform(patch("/api/family-groups/{familyGroupId}", FAMILY_GROUP_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private FamilyGroupResponse familyGroupResponse() {
        return new FamilyGroupResponse(FAMILY_GROUP_ID, "Familia Gomez", Instant.parse("2026-01-01T00:00:00Z"));
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

    private RequestPostProcessor asMember() {
        AuthenticatedUser principal = new AuthenticatedUser(3L, CLUB_ID, UserRole.MEMBER, 5L);

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
