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
import com.lazzariniingenieria.clubmanagementapi.dto.CreateReservationRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationAlreadyCancelledException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationSlotTakenException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.ReservationService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    private static final String FIXTURES_PATH = "fixtures/reservation/";
    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long RESERVATION_ID = 20L;
    private static final LocalDate DATE = LocalDate.parse("2026-11-09");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnCreatedReservationWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-reservation-request-valid.json");
        when(reservationService.createReservation(any(AuthenticatedUser.class), any(CreateReservationRequest.class)))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/api/reservations")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(20)))
                .andExpect(jsonPath("$.date", is("2026-11-09")))
                .andExpect(jsonPath("$.startTime", is("22:00:00")))
                .andExpect(jsonPath("$.source", is("MEMBER")))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    void shouldReturnCreatedReservationWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("create-reservation-request-valid.json");
        when(reservationService.createReservation(any(AuthenticatedUser.class), any(CreateReservationRequest.class)))
                .thenReturn(reservationResponse(ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/api/reservations")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnBadRequestWhenCourtIdIsMissing() throws Exception {
        String requestBody = readFixture("create-reservation-request-missing-court.json");

        mockMvc.perform(post("/api/reservations")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("courtId: courtId is required")));

        verifyNoInteractions(reservationService);
    }

    @Test
    void shouldReturnBadRequestWhenReservationBreaksARule() throws Exception {
        String requestBody = readFixture("create-reservation-request-valid.json");
        when(reservationService.createReservation(any(AuthenticatedUser.class), any(CreateReservationRequest.class)))
                .thenThrow(new InvalidReservationException("reservation cannot start in the past"));

        mockMvc.perform(post("/api/reservations")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("reservation cannot start in the past")));
    }

    @Test
    void shouldReturnConflictWhenTheSlotIsTaken() throws Exception {
        String requestBody = readFixture("create-reservation-request-valid.json");
        when(reservationService.createReservation(any(AuthenticatedUser.class), any(CreateReservationRequest.class)))
                .thenThrow(new ReservationSlotTakenException("court 3 is already reserved at that time"));

        mockMvc.perform(post("/api/reservations")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenMemberIsOfAnotherClub() throws Exception {
        String requestBody = readFixture("create-reservation-request-valid.json");
        when(reservationService.createReservation(any(AuthenticatedUser.class), any(CreateReservationRequest.class)))
                .thenThrow(new MemberNotFoundException(10L));

        mockMvc.perform(post("/api/reservations")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListReservationsOfTheDay() throws Exception {
        when(reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(DATE, null)))
                .thenReturn(List.of(reservationResponse(ReservationStatus.CONFIRMED)));

        mockMvc.perform(get("/api/reservations").with(asAdmin()).param("date", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(20)))
                .andExpect(jsonPath("$[0].memberId", is(10)));
    }

    @Test
    void shouldListReservationsOfOneCourt() throws Exception {
        when(reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(DATE, COURT_ID))).thenReturn(List.of());

        mockMvc.perform(get("/api/reservations")
                        .with(asAdmin())
                        .param("date", "2026-11-09")
                        .param("courtId", "3"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBadRequestWhenListingWithoutADate() throws Exception {
        mockMvc.perform(get("/api/reservations").with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("date: date is required")));

        verifyNoInteractions(reservationService);
    }

    @Test
    void shouldListTheUpcomingReservationsOfACourt() throws Exception {
        when(reservationService.listUpcomingReservations(CLUB_ID, COURT_ID))
                .thenReturn(List.of(reservationResponse(ReservationStatus.CONFIRMED)));

        mockMvc.perform(get("/api/reservations/upcoming").with(asAdmin()).param("courtId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(20)));
    }

    @Test
    void shouldReturnBadRequestWhenListingUpcomingWithoutACourt() throws Exception {
        mockMvc.perform(get("/api/reservations/upcoming").with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("courtId: courtId is required")));

        verifyNoInteractions(reservationService);
    }

    @Test
    void shouldCancelReservation() throws Exception {
        when(reservationService.cancelReservation(any(AuthenticatedUser.class), eq(RESERVATION_ID)))
                .thenReturn(reservationResponse(ReservationStatus.CANCELLED));

        mockMvc.perform(patch("/api/reservations/{reservationId}/cancel", RESERVATION_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void shouldReturnConflictWhenCancellingTwice() throws Exception {
        when(reservationService.cancelReservation(any(AuthenticatedUser.class), eq(RESERVATION_ID)))
                .thenThrow(new ReservationAlreadyCancelledException(RESERVATION_ID));

        mockMvc.perform(patch("/api/reservations/{reservationId}/cancel", RESERVATION_ID).with(asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenCancellingMissingReservation() throws Exception {
        when(reservationService.cancelReservation(any(AuthenticatedUser.class), eq(RESERVATION_ID)))
                .thenThrow(new ReservationNotFoundException(RESERVATION_ID));

        mockMvc.perform(patch("/api/reservations/{reservationId}/cancel", RESERVATION_ID).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        mockMvc.perform(get("/api/reservations").with(asMember()).param("date", "2026-11-09"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        mockMvc.perform(get("/api/reservations").param("date", "2026-11-09"))
                .andExpect(status().isUnauthorized());
    }

    private ReservationResponse reservationResponse(ReservationStatus status) {
        return new ReservationResponse(RESERVATION_ID, COURT_ID, 10L, ReservationSource.MEMBER, DATE, LocalTime.of(22, 0),
                LocalTime.of(23, 0), status, Instant.parse("2026-11-09T12:00:00Z"), 2L, null, null);
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
