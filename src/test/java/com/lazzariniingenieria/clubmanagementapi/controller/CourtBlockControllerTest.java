package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.AffectedReservationsSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtBlockRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtBlockAffectsUnseenReservationsException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtBlockNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.CourtBlockService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CourtBlockController.class)
@Import(SecurityConfig.class)
class CourtBlockControllerTest {

    private static final String FIXTURES_PATH = "fixtures/court-block/";
    private static final String BLOCKS_PATH = "/api/courts/{courtId}/blocks";
    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long COURT_BLOCK_ID = 8L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourtBlockService courtBlockService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnCreatedCourtBlockWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-court-block-request-valid.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenReturn(courtBlockResponse());

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(8)))
                .andExpect(jsonPath("$.courtId", is(3)))
                .andExpect(jsonPath("$.startsAt", is("2026-08-03T08:00:00")))
                .andExpect(jsonPath("$.endsAt", is("2026-08-05T23:59:00")));
    }

    @Test
    void shouldReturnCreatedCourtBlockWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-court-block-request-valid.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenReturn(courtBlockResponse());

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReadTheBlockRangeAsLocalDateTimes() throws Exception {
        String requestBody = readFixture("create-court-block-request-valid.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenReturn(courtBlockResponse());

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCourtBlockRequest> requestCaptor = ArgumentCaptor.forClass(CreateCourtBlockRequest.class);
        verify(courtBlockService).createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), requestCaptor.capture());
        CreateCourtBlockRequest request = requestCaptor.getValue();

        assertThat(request.startsAt()).isEqualTo(LocalDateTime.parse("2026-08-03T08:00"));
        assertThat(request.reason()).isEqualTo("Mantenimiento de superficie");
    }

    @Test
    void shouldReadTheReservationIdsTheAdminSaw() throws Exception {
        String requestBody = readFixture("create-court-block-request-with-reservations.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenReturn(courtBlockResponse());

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCourtBlockRequest> requestCaptor = ArgumentCaptor.forClass(CreateCourtBlockRequest.class);
        verify(courtBlockService).createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), requestCaptor.capture());

        assertThat(requestCaptor.getValue().reservationIdsToCancel()).containsExactly(45L, 46L);
    }

    @Test
    void shouldReturnConflictListingTheReservationsTheAdminDidNotSee() throws Exception {
        String requestBody = readFixture("create-court-block-request-with-reservations.json");
        List<String> unseen = List.of("reservation 52 on 2026-08-04 18:00-19:00");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenThrow(new CourtBlockAffectsUnseenReservationsException(unseen));

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("court block would cancel reservations not shown to the admin")))
                .andExpect(jsonPath("$.details[0]", is("reservation 52 on 2026-08-04 18:00-19:00")));
    }

    @Test
    void shouldPreviewTheReservationsTheBlockWouldCancel() throws Exception {
        AffectedReservationsSearchRequest filter = new AffectedReservationsSearchRequest(LocalDateTime.parse("2026-08-03T08:00"),
                LocalDateTime.parse("2026-08-05T23:59"));
        ReservationResponse activity = new ReservationResponse(45L, COURT_ID, null, ReservationSource.ACTIVITY, 30L, "Escuela de tenis",
                LocalDate.parse("2026-08-04"), LocalTime.of(17, 0), LocalTime.of(18, 0), ReservationStatus.CONFIRMED,
                Instant.parse("2026-07-30T12:00:00Z"), 2L, null, null);
        when(courtBlockService.listAffectedReservations(CLUB_ID, COURT_ID, filter)).thenReturn(List.of(activity));

        mockMvc.perform(get(BLOCKS_PATH + "/affected-reservations", COURT_ID)
                        .with(asAdmin())
                        .param("startsAt", "2026-08-03T08:00")
                        .param("endsAt", "2026-08-05T23:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(45)))
                .andExpect(jsonPath("$[0].source", is("ACTIVITY")))
                .andExpect(jsonPath("$[0].description", is("Escuela de tenis")));
    }

    @Test
    void shouldReturnBadRequestWhenPreviewingWithoutEndsAt() throws Exception {
        mockMvc.perform(get(BLOCKS_PATH + "/affected-reservations", COURT_ID)
                        .with(asAdmin())
                        .param("startsAt", "2026-08-03T08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("endsAt: endsAt is required")));

        verifyNoInteractions(courtBlockService);
    }

    @Test
    void shouldReturnBadRequestWhenEndsAtIsMissing() throws Exception {
        String requestBody = readFixture("create-court-block-request-missing-ends-at.json");

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("endsAt: endsAt is required")));

        verifyNoInteractions(courtBlockService);
    }

    @Test
    void shouldReturnBadRequestWhenReasonIsTooLong() throws Exception {
        String requestBody = readFixture("create-court-block-request-reason-too-long.json");

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("reason: reason must be at most 150 characters")));
    }

    @Test
    void shouldReturnBadRequestWhenBlockDoesNotEndAfterItStarts() throws Exception {
        String requestBody = readFixture("create-court-block-request-valid.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenThrow(new InvalidDateRangeException("endsAt must be after startsAt"));

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenBlockingCourtOfAnotherClub() throws Exception {
        String requestBody = readFixture("create-court-block-request-valid.json");
        when(courtBlockService.createCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), any(CreateCourtBlockRequest.class)))
                .thenThrow(new CourtNotFoundException(COURT_ID));

        mockMvc.perform(post(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        mockMvc.perform(get(BLOCKS_PATH, COURT_ID).with(asMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        mockMvc.perform(get(BLOCKS_PATH, COURT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListBlocksOfTheCourtFilteredByDateRange() throws Exception {
        CourtBlockSearchRequest filter = new CourtBlockSearchRequest(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        when(courtBlockService.listCourtBlocks(CLUB_ID, COURT_ID, filter)).thenReturn(List.of(courtBlockResponse()));

        mockMvc.perform(get(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(8)))
                .andExpect(jsonPath("$[0].reason", is("Mantenimiento de superficie")));
    }

    @Test
    void shouldListEveryBlockOfTheCourtWhenNoRangeIsGiven() throws Exception {
        when(courtBlockService.listCourtBlocks(CLUB_ID, COURT_ID, new CourtBlockSearchRequest(null, null)))
                .thenReturn(List.of(courtBlockResponse()));

        mockMvc.perform(get(BLOCKS_PATH, COURT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(8)));
    }

    @Test
    void shouldReturnBadRequestWhenFromCannotBeParsed() throws Exception {
        mockMvc.perform(get(BLOCKS_PATH, COURT_ID)
                        .with(asAdmin())
                        .param("from", "03/08/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("from: invalid value")));

        verifyNoInteractions(courtBlockService);
    }

    @Test
    void shouldDeleteCourtBlockAndReturnNoContent() throws Exception {
        mockMvc.perform(delete(BLOCKS_PATH + "/{courtBlockId}", COURT_ID, COURT_BLOCK_ID).with(asAdmin()))
                .andExpect(status().isNoContent());

        verify(courtBlockService).deleteCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), eq(COURT_BLOCK_ID));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingCourtBlock() throws Exception {
        doThrow(new CourtBlockNotFoundException(COURT_BLOCK_ID))
                .when(courtBlockService)
                .deleteCourtBlock(any(AuthenticatedUser.class), eq(COURT_ID), eq(COURT_BLOCK_ID));

        mockMvc.perform(delete(BLOCKS_PATH + "/{courtBlockId}", COURT_ID, COURT_BLOCK_ID).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    private CourtBlockResponse courtBlockResponse() {
        return new CourtBlockResponse(COURT_BLOCK_ID, COURT_ID, LocalDateTime.parse("2026-08-03T08:00"),
                LocalDateTime.parse("2026-08-05T23:59"), "Mantenimiento de superficie", Instant.parse("2026-07-30T12:00:00Z"), 2L);
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
