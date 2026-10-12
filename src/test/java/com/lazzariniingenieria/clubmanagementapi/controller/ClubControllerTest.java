package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.ClubResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateClubMonthlyFeeRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.ClubService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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

@WebMvcTest(ClubController.class)
@Import(SecurityConfig.class)
class ClubControllerTest {

    private static final String FIXTURES_PATH = "fixtures/club/";
    private static final Long CLUB_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClubService clubService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnClubOfTheTokenWhenRequesterIsAdmin() throws Exception {
        when(clubService.getClub(CLUB_ID)).thenReturn(clubResponse(new BigDecimal("15000.00")));

        mockMvc.perform(get("/api/clubs/me").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Club 1")))
                .andExpect(jsonPath("$.monthlyFeeAmount", is(15000.00)));
    }

    @Test
    void shouldReturnNullMonthlyFeeWhenClubHasNotConfiguredOne() throws Exception {
        when(clubService.getClub(CLUB_ID)).thenReturn(clubResponse(null));

        mockMvc.perform(get("/api/clubs/me").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyFeeAmount", nullValue()));
    }

    @Test
    void shouldReturnNotFoundWhenTokenClubHasNoRow() throws Exception {
        when(clubService.getClub(CLUB_ID)).thenThrow(new ClubNotFoundException(CLUB_ID));

        mockMvc.perform(get("/api/clubs/me").with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenMemberRequestsClub() throws Exception {
        mockMvc.perform(get("/api/clubs/me").with(asMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenRequestingClubWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/clubs/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldUpdateMonthlyFeeWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-valid.json");
        when(clubService.updateMonthlyFee(any(AuthenticatedUser.class), any(UpdateClubMonthlyFeeRequest.class)))
                .thenReturn(clubResponse(new BigDecimal("15000.00")));

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyFeeAmount", is(15000.00)))
                .andExpect(jsonPath("$.updatedByUserId", is(1)));
    }

    @Test
    void shouldPassNullMonthlyFeeToServiceSoTheReferenceCanBeCleared() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-null.json");
        when(clubService.updateMonthlyFee(any(AuthenticatedUser.class), any(UpdateClubMonthlyFeeRequest.class)))
                .thenReturn(clubResponse(null));

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(clubService).updateMonthlyFee(any(AuthenticatedUser.class), eq(new UpdateClubMonthlyFeeRequest(null)));
    }

    @Test
    void shouldReturnForbiddenWhenAdminUpdatesMonthlyFee() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-valid.json");

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        verify(clubService, never()).updateMonthlyFee(any(), any());
    }

    @Test
    void shouldReturnForbiddenWhenMemberUpdatesMonthlyFee() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-valid.json");

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnBadRequestWhenMonthlyFeeIsNotPositive() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-non-positive.json");

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(clubService, never()).updateMonthlyFee(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenMonthlyFeeHasMoreThanTwoDecimals() throws Exception {
        String requestBody = readFixture("update-club-monthly-fee-request-too-many-digits.json");

        mockMvc.perform(patch("/api/clubs/me")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private ClubResponse clubResponse(BigDecimal monthlyFeeAmount) {
        return new ClubResponse(CLUB_ID, "Club 1", monthlyFeeAmount, Instant.parse("2026-10-01T12:00:00Z"), 1L);
    }

    private RequestPostProcessor asSuperAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    private RequestPostProcessor asAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(5L, CLUB_ID, UserRole.ADMIN, null);

        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private RequestPostProcessor asMember() {
        AuthenticatedUser principal = new AuthenticatedUser(3L, CLUB_ID, UserRole.MEMBER, 7L);

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
