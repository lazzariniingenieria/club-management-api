package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateCourtNameException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCourtHoursException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.CourtService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
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

@WebMvcTest(CourtController.class)
@Import(SecurityConfig.class)
class CourtControllerTest {

    private static final String FIXTURES_PATH = "fixtures/court/";
    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourtService courtService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnCreatedCourtWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-court-request-valid.json");
        when(courtService.createCourt(any(AuthenticatedUser.class), any(CreateCourtRequest.class))).thenReturn(courtResponse(true));

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(3)))
                .andExpect(jsonPath("$.name", is("Cancha de Padel 1")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void shouldReturnCreatedCourtWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-court-request-valid.json");
        when(courtService.createCourt(any(AuthenticatedUser.class), any(CreateCourtRequest.class))).thenReturn(courtResponse(true));

        mockMvc.perform(post("/api/courts")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldSendHoursAsIsoLocalTimes() throws Exception {
        when(courtService.getCourt(CLUB_ID, COURT_ID)).thenReturn(courtResponse(true));

        mockMvc.perform(get("/api/courts/{courtId}", COURT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingTime", is("08:00:00")))
                .andExpect(jsonPath("$.closingTime", is("23:00:00")));
    }

    @Test
    void shouldReturnConflictWhenCreatingCourtWithDuplicateName() throws Exception {
        String requestBody = readFixture("create-court-request-valid.json");
        when(courtService.createCourt(any(AuthenticatedUser.class), any(CreateCourtRequest.class)))
                .thenThrow(new DuplicateCourtNameException("Cancha de Padel 1"));

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenCourtClosesBeforeItOpens() throws Exception {
        String requestBody = readFixture("create-court-request-valid.json");
        when(courtService.createCourt(any(AuthenticatedUser.class), any(CreateCourtRequest.class)))
                .thenThrow(new InvalidCourtHoursException(LocalTime.of(23, 0), LocalTime.of(8, 0)));

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        String requestBody = readFixture("create-court-request-blank-name.json");

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("name: name is required")));

        verifyNoInteractions(courtService);
    }

    @Test
    void shouldReturnBadRequestWhenOpeningTimeIsMissing() throws Exception {
        String requestBody = readFixture("create-court-request-missing-opening-time.json");

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("openingTime: openingTime is required")));
    }

    @Test
    void shouldReturnBadRequestWhenATimeCannotBeParsed() throws Exception {
        String requestBody = readFixture("create-court-request-invalid-opening-time.json");

        mockMvc.perform(post("/api/courts")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courtService);
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        mockMvc.perform(get("/api/courts").with(asMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        mockMvc.perform(get("/api/courts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnListOfCourts() throws Exception {
        when(courtService.listCourts(CLUB_ID)).thenReturn(List.of(courtResponse(true)));

        mockMvc.perform(get("/api/courts").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(3)))
                .andExpect(jsonPath("$[0].type", is("Padel")));
    }

    @Test
    void shouldReturnNotFoundWhenCourtDoesNotExist() throws Exception {
        when(courtService.getCourt(CLUB_ID, COURT_ID)).thenThrow(new CourtNotFoundException(COURT_ID));

        mockMvc.perform(get("/api/courts/{courtId}", COURT_ID).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateCourtWhenValid() throws Exception {
        String requestBody = readFixture("update-court-request-valid.json");
        when(courtService.updateCourt(any(AuthenticatedUser.class), eq(COURT_ID), any(UpdateCourtRequest.class)))
                .thenReturn(courtResponse(true));

        mockMvc.perform(patch("/api/courts/{courtId}", COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(3)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingWithoutClosingTime() throws Exception {
        String requestBody = readFixture("update-court-request-missing-closing-time.json");

        mockMvc.perform(patch("/api/courts/{courtId}", COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("closingTime: closingTime is required")));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingCourt() throws Exception {
        String requestBody = readFixture("update-court-request-valid.json");
        when(courtService.updateCourt(any(AuthenticatedUser.class), eq(COURT_ID), any(UpdateCourtRequest.class)))
                .thenThrow(new CourtNotFoundException(COURT_ID));

        mockMvc.perform(patch("/api/courts/{courtId}", COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeactivateCourt() throws Exception {
        when(courtService.deactivateCourt(any(AuthenticatedUser.class), eq(COURT_ID))).thenReturn(courtResponse(false));

        mockMvc.perform(patch("/api/courts/{courtId}/deactivate", COURT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void shouldReactivateCourt() throws Exception {
        when(courtService.reactivateCourt(any(AuthenticatedUser.class), eq(COURT_ID))).thenReturn(courtResponse(true));

        mockMvc.perform(patch("/api/courts/{courtId}/reactivate", COURT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    private CourtResponse courtResponse(boolean active) {
        return new CourtResponse(COURT_ID, "Cancha de Padel 1", "Padel", LocalTime.of(8, 0), LocalTime.of(23, 0), active,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), 2L, 2L);
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
