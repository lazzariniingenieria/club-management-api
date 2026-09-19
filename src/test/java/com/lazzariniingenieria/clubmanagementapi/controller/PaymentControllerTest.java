package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.PaymentService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String FIXTURES_PATH = "fixtures/payment/";
    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnCreatedPaymentsWhenRequesterIsAdmin() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenReturn(List.of(paymentResponse()));

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].memberId", is(1)))
                .andExpect(jsonPath("$[0].amount", is(15000)))
                .andExpect(jsonPath("$[0].paymentMethod", is("CASH")));
    }

    @Test
    void shouldReturnNotFoundWhenRecordingPaymentForMissingMember() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenPaidByMemberIsNotInSameFamilyGroup() throws Exception {
        String requestBody = readFixture("record-payment-request-with-paid-by.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenThrow(new PaidByMemberNotInFamilyGroupException(2L, 1L));

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenPeriodsCoveredContainDuplicates() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenThrow(new DuplicatePeriodCoveredException());

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenMemberIdIsMissing() throws Exception {
        String requestBody = readFixture("record-payment-request-missing-member-id.json");

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenPeriodsCoveredIsEmpty() throws Exception {
        String requestBody = readFixture("record-payment-request-empty-periods.json");

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsNotPositive() throws Exception {
        String requestBody = readFixture("record-payment-request-non-positive-amount.json");

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnForbiddenWhenRequesterIsMember() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");

        mockMvc.perform(post("/api/payments")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthenticationPresent() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnPaymentHistoryForMember() throws Exception {
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID)).thenReturn(List.of(paymentResponse()));

        mockMvc.perform(get("/api/members/{memberId}/payments", MEMBER_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId", is(1)));
    }

    @Test
    void shouldReturnNotFoundWhenListingPaymentsForMissingMember() throws Exception {
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID)).thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(get("/api/members/{memberId}/payments", MEMBER_ID).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnDelinquencyReportOrderedByDaysOverdue() throws Exception {
        MemberDelinquencyResponse delinquent = new MemberDelinquencyResponse(MEMBER_ID, "Marcos", "Gomez",
                LocalDate.parse("2026-05-01"), 45L);
        when(paymentService.listMemberDelinquency(CLUB_ID)).thenReturn(List.of(delinquent));

        mockMvc.perform(get("/api/payments/delinquency").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId", is(1)))
                .andExpect(jsonPath("$[0].daysOverdue", is(45)));
    }

    @Test
    void shouldReturnForbiddenWhenMemberRequestsDelinquencyReport() throws Exception {
        mockMvc.perform(get("/api/payments/delinquency").with(asMember()))
                .andExpect(status().isForbidden());
    }

    private PaymentResponse paymentResponse() {
        return new PaymentResponse(10L, MEMBER_ID, null, new BigDecimal("15000"), Instant.parse("2026-07-01T00:00:00Z"),
                LocalDate.parse("2026-07-01"), PaymentMethod.CASH, 5L, Instant.parse("2026-07-01T00:00:00Z"));
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
