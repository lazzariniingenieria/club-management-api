package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.FeeReceiptService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(FeeReceiptController.class)
@Import(SecurityConfig.class)
class FeeReceiptControllerTest {

    private static final String RECEIPTS_PATH = "/api/payments/receipts";
    private static final Long CLUB_ID = 1L;
    private static final LocalDate PAID_FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate PAID_TO = LocalDate.parse("2026-10-15");
    private static final byte[] PDF = {37, 80, 68, 70};
    private static final String INVERTED_RANGE_MESSAGE = "paidFrom 2026-10-15 must not be after paidTo 2026-10-01";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeeReceiptService feeReceiptService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldReturnThePdfAsADownloadableAttachmentWhenRequesterIsAdmin() throws Exception {
        when(feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO)).thenReturn(Optional.of(PDF));

        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidFrom", "2026-10-01")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"cuotas-cobradas-2026-10-01-al-2026-10-15.pdf\""))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void shouldReturnThePdfWhenRequesterIsSuperAdmin() throws Exception {
        when(feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO)).thenReturn(Optional.of(PDF));

        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asSuperAdmin())
                        .param("paidFrom", "2026-10-01")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnNoContentWhenNoPaymentsWereCollectedInTheRange() throws Exception {
        when(feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_FROM, PAID_TO)).thenReturn(Optional.empty());

        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidFrom", "2026-10-01")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void shouldReturnBadRequestWithoutCallingTheServiceWhenPaidFromIsMissing() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("paidFrom: paidFrom is required")));

        verify(feeReceiptService, never()).generateReceiptsPdf(anyLong(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWithoutCallingTheServiceWhenPaidToIsMissing() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidFrom", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("paidTo: paidTo is required")));

        verify(feeReceiptService, never()).generateReceiptsPdf(anyLong(), any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenADateIsNotIsoFormatted() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidFrom", "01/10/2026")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("paidFrom: invalid value")));
    }

    @Test
    void shouldReturnBadRequestWhenPaidFromIsAfterPaidTo() throws Exception {
        when(feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_TO, PAID_FROM))
                .thenThrow(new InvalidDateRangeException(INVERTED_RANGE_MESSAGE));

        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .param("paidFrom", "2026-10-15")
                        .param("paidTo", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(INVERTED_RANGE_MESSAGE)));
    }

    @Test
    void shouldReturnJsonErrorWhenAParameterIsMissingAndClientAcceptsOnlyPdf() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_PDF_VALUE)
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.details", hasItem("paidFrom: paidFrom is required")));
    }

    @Test
    void shouldReturnJsonErrorWhenRangeIsInvertedAndClientAcceptsOnlyPdf() throws Exception {
        when(feeReceiptService.generateReceiptsPdf(CLUB_ID, PAID_TO, PAID_FROM))
                .thenThrow(new InvalidDateRangeException(INVERTED_RANGE_MESSAGE));

        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asAdmin())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_PDF_VALUE)
                        .param("paidFrom", "2026-10-15")
                        .param("paidTo", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is(INVERTED_RANGE_MESSAGE)));
    }

    @Test
    void shouldReturnForbiddenWhenMemberRequestsReceipts() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .with(asMember())
                        .param("paidFrom", "2026-10-01")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isForbidden());

        verify(feeReceiptService, never()).generateReceiptsPdf(anyLong(), any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenRequestingReceiptsWithoutAuthentication() throws Exception {
        mockMvc.perform(get(RECEIPTS_PATH)
                        .param("paidFrom", "2026-10-01")
                        .param("paidTo", "2026-10-15"))
                .andExpect(status().isUnauthorized());
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
}
