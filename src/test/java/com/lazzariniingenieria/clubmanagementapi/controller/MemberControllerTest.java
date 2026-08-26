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
import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateDniException;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.MemberService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    private static final String FIXTURES_PATH = "fixtures/member/";
    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnCreatedMemberWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-member-request-valid.json");
        MemberResponse response = memberResponse();
        when(memberService.createMember(eq(CLUB_ID), any(CreateMemberRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/members")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.dni", is("30111222")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void shouldReturnCreatedMemberWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-member-request-valid.json");
        when(memberService.createMember(eq(CLUB_ID), any(CreateMemberRequest.class))).thenReturn(memberResponse());

        mockMvc.perform(post("/api/members")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnConflictWhenCreatingMemberWithDuplicateDni() throws Exception {
        String requestBody = readFixture("create-member-request-valid.json");
        when(memberService.createMember(eq(CLUB_ID), any(CreateMemberRequest.class)))
                .thenThrow(new DuplicateDniException("30111222"));

        mockMvc.perform(post("/api/members")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenFirstNameIsBlank() throws Exception {
        String requestBody = readFixture("create-member-request-blank-first-name.json");

        mockMvc.perform(post("/api/members")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenDniIsBlank() throws Exception {
        String requestBody = readFixture("create-member-request-blank-dni.json");

        mockMvc.perform(post("/api/members")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenEmailFormatIsInvalid() throws Exception {
        String requestBody = readFixture("create-member-request-invalid-email.json");

        mockMvc.perform(post("/api/members")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("email: email must be a valid address")));
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        String requestBody = readFixture("create-member-request-valid.json");

        mockMvc.perform(post("/api/members")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        String requestBody = readFixture("create-member-request-valid.json");

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnListOfMembers() throws Exception {
        when(memberService.listMembers(CLUB_ID)).thenReturn(List.of(memberResponse()));

        mockMvc.perform(get("/api/members").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(10)));
    }

    @Test
    void shouldReturnMemberByIdWhenFound() throws Exception {
        when(memberService.getMember(CLUB_ID, MEMBER_ID)).thenReturn(memberResponse());

        mockMvc.perform(get("/api/members/{memberId}", MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni", is("30111222")));
    }

    @Test
    void shouldReturnNotFoundWhenMemberDoesNotExist() throws Exception {
        when(memberService.getMember(CLUB_ID, MEMBER_ID)).thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(get("/api/members/{memberId}", MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateMemberWhenValid() throws Exception {
        String requestBody = readFixture("update-member-request-valid.json");
        when(memberService.updateMember(eq(CLUB_ID), eq(MEMBER_ID), any(UpdateMemberRequest.class)))
                .thenReturn(memberResponse());

        mockMvc.perform(patch("/api/members/{memberId}", MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingWithBlankDni() throws Exception {
        String requestBody = readFixture("update-member-request-blank-dni.json");

        mockMvc.perform(patch("/api/members/{memberId}", MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeactivateMember() throws Exception {
        MemberResponse deactivated = new MemberResponse(MEMBER_ID, "Marcos", "Gomez", "30111222", "+54 11 4444-5555",
                "marcos@example.com", 1L, LocalDate.parse("2026-01-01"), MemberStatus.INACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(memberService.deactivateMember(CLUB_ID, MEMBER_ID)).thenReturn(deactivated);

        mockMvc.perform(patch("/api/members/{memberId}/deactivate", MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INACTIVE")));
    }

    @Test
    void shouldReactivateMember() throws Exception {
        when(memberService.reactivateMember(CLUB_ID, MEMBER_ID)).thenReturn(memberResponse());

        mockMvc.perform(patch("/api/members/{memberId}/reactivate", MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void shouldAssignFamilyGroupWhenValid() throws Exception {
        String requestBody = readFixture("assign-family-group-request-valid.json");
        when(memberService.assignFamilyGroup(eq(CLUB_ID), eq(MEMBER_ID), eq(2L))).thenReturn(memberResponse());

        mockMvc.perform(patch("/api/members/{memberId}/family-group", MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void shouldUnassignFamilyGroupWhenFamilyGroupIdIsNull() throws Exception {
        String requestBody = readFixture("assign-family-group-request-null.json");
        when(memberService.assignFamilyGroup(CLUB_ID, MEMBER_ID, null)).thenReturn(memberResponse());

        mockMvc.perform(patch("/api/members/{memberId}/family-group", MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnNotFoundWhenAssigningMissingFamilyGroup() throws Exception {
        String requestBody = readFixture("assign-family-group-request-valid.json");
        when(memberService.assignFamilyGroup(eq(CLUB_ID), eq(MEMBER_ID), eq(2L)))
                .thenThrow(new FamilyGroupNotFoundException(2L));

        mockMvc.perform(patch("/api/members/{memberId}/family-group", MEMBER_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    private MemberResponse memberResponse() {
        return new MemberResponse(MEMBER_ID, "Marcos", "Gomez", "30111222", "+54 11 4444-5555",
                "marcos@example.com", 1L, LocalDate.parse("2026-01-01"), MemberStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"));
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
