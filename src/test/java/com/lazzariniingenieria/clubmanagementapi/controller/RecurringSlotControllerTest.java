package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RenewRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotAlreadyInactiveException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotConflictException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.RecurringSlotService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
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

@WebMvcTest(RecurringSlotController.class)
@Import(SecurityConfig.class)
class RecurringSlotControllerTest {

    private static final String FIXTURES_PATH = "fixtures/recurring-slot/";
    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long MEMBER_ID = 10L;
    private static final Long RECURRING_SLOT_ID = 30L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecurringSlotService recurringSlotService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnCreatedRecurringSlotWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("create-recurring-slot-request-valid.json");
        when(recurringSlotService.createRecurringSlot(any(AuthenticatedUser.class), any(CreateRecurringSlotRequest.class)))
                .thenReturn(recurringSlotResponse(true, RecurringSlotState.CURRENT));

        mockMvc.perform(post("/api/recurring-slots")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(30)))
                .andExpect(jsonPath("$.dayOfWeek", is("TUESDAY")))
                .andExpect(jsonPath("$.startTime", is("18:00:00")))
                .andExpect(jsonPath("$.validUntil", is("2027-03-30")))
                .andExpect(jsonPath("$.state", is("CURRENT")));
    }

    @Test
    void shouldReadTheDayOfWeekByItsEnglishName() throws Exception {
        String requestBody = readFixture("create-recurring-slot-request-valid.json");
        CreateRecurringSlotRequest expected = new CreateRecurringSlotRequest(COURT_ID, MEMBER_ID, null, DayOfWeek.TUESDAY,
                LocalTime.of(18, 0), LocalTime.of(19, 0), LocalDate.parse("2026-11-10"), LocalDate.parse("2027-03-30"));
        when(recurringSlotService.createRecurringSlot(any(AuthenticatedUser.class), eq(expected)))
                .thenReturn(recurringSlotResponse(true, RecurringSlotState.CURRENT));

        mockMvc.perform(post("/api/recurring-slots")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnBadRequestWhenValidUntilIsMissing() throws Exception {
        String requestBody = readFixture("create-recurring-slot-request-missing-valid-until.json");

        mockMvc.perform(post("/api/recurring-slots")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("validUntil: validUntil is required")));

        verifyNoInteractions(recurringSlotService);
    }

    @Test
    void shouldReturnBadRequestWhenDayOfWeekIsUnknown() throws Exception {
        String requestBody = readFixture("create-recurring-slot-request-invalid-day-of-week.json");

        mockMvc.perform(post("/api/recurring-slots")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringSlotService);
    }

    @Test
    void shouldReturnConflictListingTheConflictingDates() throws Exception {
        String requestBody = readFixture("create-recurring-slot-request-valid.json");
        List<String> conflicts = List.of("2026-11-17: court already reserved", "2026-11-24: court blocked");
        when(recurringSlotService.createRecurringSlot(any(AuthenticatedUser.class), any(CreateRecurringSlotRequest.class)))
                .thenThrow(new RecurringSlotConflictException(conflicts));

        mockMvc.perform(post("/api/recurring-slots")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("recurring slot conflicts with existing reservations or court blocks")))
                .andExpect(jsonPath("$.details[0]", is("2026-11-17: court already reserved")))
                .andExpect(jsonPath("$.details[1]", is("2026-11-24: court blocked")));
    }

    @Test
    void shouldListWithTheGivenFilters() throws Exception {
        RecurringSlotSearchRequest filter = new RecurringSlotSearchRequest(COURT_ID, MEMBER_ID, RecurringSlotState.EXPIRED);
        when(recurringSlotService.listRecurringSlots(CLUB_ID, filter)).thenReturn(List.of(recurringSlotResponse(true,
                RecurringSlotState.EXPIRED)));

        mockMvc.perform(get("/api/recurring-slots")
                        .with(asAdmin())
                        .param("courtId", "3")
                        .param("memberId", "10")
                        .param("state", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state", is("EXPIRED")));
    }

    @Test
    void shouldListWithoutFilters() throws Exception {
        when(recurringSlotService.listRecurringSlots(CLUB_ID, new RecurringSlotSearchRequest(null, null, null))).thenReturn(List.of());

        mockMvc.perform(get("/api/recurring-slots").with(asAdmin()))
                .andExpect(status().isOk());

        verify(recurringSlotService).listRecurringSlots(CLUB_ID, new RecurringSlotSearchRequest(null, null, null));
    }

    @Test
    void shouldReturnBadRequestWhenStateIsUnknown() throws Exception {
        mockMvc.perform(get("/api/recurring-slots").with(asAdmin()).param("state", "VIGENTE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringSlotService);
    }

    @Test
    void shouldGetOneRecurringSlot() throws Exception {
        when(recurringSlotService.getRecurringSlot(CLUB_ID, RECURRING_SLOT_ID)).thenReturn(recurringSlotResponse(true,
                RecurringSlotState.CURRENT));

        mockMvc.perform(get("/api/recurring-slots/{recurringSlotId}", RECURRING_SLOT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId", is(10)));
    }

    @Test
    void shouldReturnNotFoundWhenTheRecurringSlotIsMissing() throws Exception {
        RecurringSlotNotFoundException notFound = new RecurringSlotNotFoundException(RECURRING_SLOT_ID);
        when(recurringSlotService.getRecurringSlot(CLUB_ID, RECURRING_SLOT_ID)).thenThrow(notFound);

        mockMvc.perform(get("/api/recurring-slots/{recurringSlotId}", RECURRING_SLOT_ID).with(asAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Recurring slot 30 not found")));
    }

    @Test
    void shouldDeactivateTheRecurringSlot() throws Exception {
        when(recurringSlotService.deactivateRecurringSlot(any(AuthenticatedUser.class), eq(RECURRING_SLOT_ID)))
                .thenReturn(recurringSlotResponse(false, RecurringSlotState.INACTIVE));

        mockMvc.perform(patch("/api/recurring-slots/{recurringSlotId}/deactivate", RECURRING_SLOT_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.state", is("INACTIVE")));
    }

    @Test
    void shouldReturnConflictWhenDeactivatingTwice() throws Exception {
        when(recurringSlotService.deactivateRecurringSlot(any(AuthenticatedUser.class), eq(RECURRING_SLOT_ID)))
                .thenThrow(new RecurringSlotAlreadyInactiveException(RECURRING_SLOT_ID));

        mockMvc.perform(patch("/api/recurring-slots/{recurringSlotId}/deactivate", RECURRING_SLOT_ID).with(asAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Recurring slot 30 is already inactive")));
    }

    @Test
    void shouldReturnCreatedRenewedRecurringSlot() throws Exception {
        String requestBody = readFixture("renew-recurring-slot-request-valid.json");
        RenewRecurringSlotRequest expected = new RenewRecurringSlotRequest(LocalDate.parse("2027-03-31"), LocalDate.parse("2027-11-30"));
        when(recurringSlotService.renewRecurringSlot(any(AuthenticatedUser.class), eq(RECURRING_SLOT_ID), eq(expected)))
                .thenReturn(recurringSlotResponse(true, RecurringSlotState.CURRENT));

        mockMvc.perform(post("/api/recurring-slots/{recurringSlotId}/renew", RECURRING_SLOT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnBadRequestWhenRenewingWithoutValidUntil() throws Exception {
        String requestBody = readFixture("renew-recurring-slot-request-missing-valid-until.json");

        mockMvc.perform(post("/api/recurring-slots/{recurringSlotId}/renew", RECURRING_SLOT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]", is("validUntil: validUntil is required")));

        verifyNoInteractions(recurringSlotService);
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        mockMvc.perform(get("/api/recurring-slots").with(asMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        mockMvc.perform(get("/api/recurring-slots"))
                .andExpect(status().isUnauthorized());
    }

    private RecurringSlotResponse recurringSlotResponse(boolean active, RecurringSlotState state) {
        return new RecurringSlotResponse(RECURRING_SLOT_ID, COURT_ID, MEMBER_ID, null, DayOfWeek.TUESDAY, LocalTime.of(18, 0),
                LocalTime.of(19, 0), LocalDate.parse("2026-11-10"), LocalDate.parse("2027-03-30"), active, state,
                Instant.parse("2026-11-09T12:00:00Z"), 2L, null, null);
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
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
