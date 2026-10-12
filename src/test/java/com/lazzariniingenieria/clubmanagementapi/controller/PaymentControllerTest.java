package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.VoidPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicatePeriodCoveredException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaidByMemberNotInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentAlreadyVoidedException;
import com.lazzariniingenieria.clubmanagementapi.exception.PaymentNotFoundException;
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

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String FIXTURES_PATH = "fixtures/payment/";
    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;

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
    void shouldReturnCreatedPaymentsWhenRequesterIsSuperAdmin() throws Exception {
        String requestBody = readFixture("record-payment-request-valid.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenReturn(List.of(paymentResponse()));

        mockMvc.perform(post("/api/payments")
                        .with(asSuperAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldPassEveryPeriodCoveredToTheServiceWhenMultiplePeriodsAreSent() throws Exception {
        String requestBody = readFixture("record-payment-request-multiple-periods.json");
        when(paymentService.recordPayment(any(AuthenticatedUser.class), any(RecordPaymentRequest.class)))
                .thenReturn(List.of(paymentResponse(), paymentResponse()));

        mockMvc.perform(post("/api/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()", is(2)));

        ArgumentCaptor<RecordPaymentRequest> requestCaptor = ArgumentCaptor.forClass(RecordPaymentRequest.class);
        verify(paymentService).recordPayment(any(AuthenticatedUser.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().periodsCovered())
                .containsExactly(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01"));
    }

    @Test
    void shouldReturnPaymentHistoryWhenRequesterIsSuperAdmin() throws Exception {
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, false)).thenReturn(List.of(paymentResponse()));

        mockMvc.perform(get("/api/payments/members/{memberId}", MEMBER_ID).with(asSuperAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnDelinquencyReportWhenRequesterIsSuperAdmin() throws Exception {
        when(paymentService.listMemberDelinquency(CLUB_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/delinquency").with(asSuperAdmin()))
                .andExpect(status().isOk());
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
    void shouldReturnBadRequestWhenAmountExceedsAllowedDigits() throws Exception {
        String requestBody = readFixture("record-payment-request-too-many-digits.json");

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
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, false)).thenReturn(List.of(paymentResponse()));

        mockMvc.perform(get("/api/payments/members/{memberId}", MEMBER_ID).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId", is(1)));
    }

    @Test
    void shouldReturnNotFoundWhenListingPaymentsForMissingMember() throws Exception {
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, false)).thenThrow(new MemberNotFoundException(MEMBER_ID));

        mockMvc.perform(get("/api/payments/members/{memberId}", MEMBER_ID).with(asAdmin()))
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

    @Test
    void shouldPassEveryCombinedSearchFilterToTheServiceWhenRequesterIsAdmin() throws Exception {
        when(paymentService.searchPayments(any(), any(PaymentSearchRequest.class))).thenReturn(List.of(paymentResponse()));

        mockMvc.perform(get("/api/payments")
                        .with(asAdmin())
                        .param("memberId", "1")
                        .param("from", "2026-01-01")
                        .param("to", "2026-06-01")
                        .param("paymentMethod", "TRANSFER")
                        .param("includeVoided", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(10)));

        ArgumentCaptor<PaymentSearchRequest> filterCaptor = ArgumentCaptor.forClass(PaymentSearchRequest.class);
        verify(paymentService).searchPayments(eq(CLUB_ID), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(new PaymentSearchRequest(MEMBER_ID, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-06-01"), PaymentMethod.TRANSFER, true));
    }

    @Test
    void shouldSearchWithNoFiltersAndExcludeVoidedByDefault() throws Exception {
        when(paymentService.searchPayments(any(), any(PaymentSearchRequest.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/payments").with(asSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        ArgumentCaptor<PaymentSearchRequest> filterCaptor = ArgumentCaptor.forClass(PaymentSearchRequest.class);
        verify(paymentService).searchPayments(eq(CLUB_ID), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).isEqualTo(new PaymentSearchRequest(null, null, null, null, null));
        assertThat(filterCaptor.getValue().includesVoided()).isFalse();
    }

    @Test
    void shouldReturnBadRequestWhenSearchPaymentMethodIsUnknown() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .with(asAdmin())
                        .param("paymentMethod", "CHEQUE"))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).searchPayments(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenSearchDateIsNotIsoFormatted() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .with(asAdmin())
                        .param("from", "01/07/2026"))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).searchPayments(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenSearchFromIsAfterTo() throws Exception {
        when(paymentService.searchPayments(any(), any(PaymentSearchRequest.class)))
                .thenThrow(new InvalidDateRangeException(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-06-01")));

        mockMvc.perform(get("/api/payments")
                        .with(asAdmin())
                        .param("from", "2026-07-01")
                        .param("to", "2026-06-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnForbiddenWhenMemberSearchesPayments() throws Exception {
        mockMvc.perform(get("/api/payments").with(asMember()))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).searchPayments(any(), any());
    }

    @Test
    void shouldPassIncludeVoidedToServiceWhenListingMemberHistory() throws Exception {
        when(paymentService.listPaymentsForMember(CLUB_ID, MEMBER_ID, true)).thenReturn(List.of(voidedPaymentResponse()));

        mockMvc.perform(get("/api/payments/members/{memberId}", MEMBER_ID)
                        .with(asAdmin())
                        .param("includeVoided", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voidedAt", is("2026-07-02T10:00:00Z")))
                .andExpect(jsonPath("$[0].voidReason", is("Importe mal cargado")));
    }

    @Test
    void shouldReturnVoidedPaymentWhenAdminVoidsWithReason() throws Exception {
        String requestBody = readFixture("void-payment-request-valid.json");
        when(paymentService.voidPayment(any(AuthenticatedUser.class), eq(PAYMENT_ID), any())).thenReturn(voidedPaymentResponse());

        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.voidedAt", is("2026-07-02T10:00:00Z")))
                .andExpect(jsonPath("$.voidedByUserId", is(5)));

        verify(paymentService).voidPayment(any(AuthenticatedUser.class), eq(PAYMENT_ID), eq(new VoidPaymentRequest("Importe mal cargado")));
    }

    @Test
    void shouldVoidPaymentWithoutRequestBodyWhenRequesterIsSuperAdmin() throws Exception {
        when(paymentService.voidPayment(any(AuthenticatedUser.class), eq(PAYMENT_ID), isNull())).thenReturn(voidedPaymentResponse());

        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID).with(asSuperAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnConflictWhenVoidingAnAlreadyVoidedPayment() throws Exception {
        when(paymentService.voidPayment(any(AuthenticatedUser.class), eq(PAYMENT_ID), any()))
                .thenThrow(new PaymentAlreadyVoidedException(PAYMENT_ID));

        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID).with(asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenVoidingPaymentOutsideTheClub() throws Exception {
        when(paymentService.voidPayment(any(AuthenticatedUser.class), eq(PAYMENT_ID), any()))
                .thenThrow(new PaymentNotFoundException(PAYMENT_ID));

        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenVoidReasonExceedsMaxLength() throws Exception {
        String requestBody = readFixture("void-payment-request-reason-too-long.json");

        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID)
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).voidPayment(any(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenVoidPaymentIdIsNotNumeric() throws Exception {
        mockMvc.perform(patch("/api/payments/{paymentId}/void", "abc").with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnForbiddenWhenMemberVoidsPayment() throws Exception {
        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID).with(asMember()))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).voidPayment(any(), any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenVoidingWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/api/payments/{paymentId}/void", PAYMENT_ID))
                .andExpect(status().isUnauthorized());
    }

    private PaymentResponse paymentResponse() {
        return new PaymentResponse(PAYMENT_ID, MEMBER_ID, null, new BigDecimal("15000"), Instant.parse("2026-07-01T00:00:00Z"),
                LocalDate.parse("2026-07-01"), PaymentMethod.CASH, 5L, Instant.parse("2026-07-01T00:00:00Z"), null, null, null);
    }

    private PaymentResponse voidedPaymentResponse() {
        return new PaymentResponse(PAYMENT_ID, MEMBER_ID, null, new BigDecimal("15000"), Instant.parse("2026-07-01T00:00:00Z"),
                LocalDate.parse("2026-07-01"), PaymentMethod.CASH, 5L, Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T10:00:00Z"), 5L, "Importe mal cargado");
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
