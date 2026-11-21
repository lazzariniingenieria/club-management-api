package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.OwnPaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.OwnPaymentsResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AccountNotLinkedToMemberException;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import com.lazzariniingenieria.clubmanagementapi.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    private static final Long CLUB_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private static final AuthenticatedUser MEMBER_USER = new AuthenticatedUser(3L, CLUB_ID, UserRole.MEMBER, MEMBER_ID);
    private static final AuthenticatedUser ADMIN_MEMBER_USER = new AuthenticatedUser(5L, CLUB_ID, UserRole.ADMIN, MEMBER_ID);
    private static final AuthenticatedUser SUPER_ADMIN_USER = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AccountSessionService accountSessionService;

    @Test
    void shouldReturnTheOwnPaymentsAndAccountStatusWhenRequesterIsAMember() throws Exception {
        when(paymentService.getOwnPayments(MEMBER_USER)).thenReturn(ownPaymentsResponse());

        mockMvc.perform(get("/api/me/payments").with(as(MEMBER_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPeriodCovered", is("2026-07-01")))
                .andExpect(jsonPath("$.daysOverdue", is(12)))
                .andExpect(jsonPath("$.payments.length()", is(1)))
                .andExpect(jsonPath("$.payments[0].id", is(10)))
                .andExpect(jsonPath("$.payments[0].amount", is(15000.00)))
                .andExpect(jsonPath("$.payments[0].paidAt", is("2026-07-03T14:00:00Z")))
                .andExpect(jsonPath("$.payments[0].periodCovered", is("2026-07-01")))
                .andExpect(jsonPath("$.payments[0].paymentMethod", is("CASH")));
    }

    @Test
    void shouldNotExposeWhoRecordedOrVoidedThePaymentToTheMember() throws Exception {
        when(paymentService.getOwnPayments(MEMBER_USER)).thenReturn(ownPaymentsResponse());

        mockMvc.perform(get("/api/me/payments").with(as(MEMBER_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].recordedByUserId").doesNotExist())
                .andExpect(jsonPath("$.payments[0].voidedByUserId").doesNotExist())
                .andExpect(jsonPath("$.payments[0].voidReason").doesNotExist());
    }

    @Test
    void shouldReturnTheOwnPaymentsWhenRequesterIsAnAdminWhoIsAlsoAMember() throws Exception {
        when(paymentService.getOwnPayments(ADMIN_MEMBER_USER)).thenReturn(ownPaymentsResponse());

        mockMvc.perform(get("/api/me/payments").with(as(ADMIN_MEMBER_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()", is(1)));
    }

    @Test
    void shouldReturnNotFoundWhenTheAccountIsNotLinkedToAMember() throws Exception {
        when(paymentService.getOwnPayments(SUPER_ADMIN_USER)).thenThrow(new AccountNotLinkedToMemberException(1L));

        mockMvc.perform(get("/api/me/payments").with(as(SUPER_ADMIN_USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("account 1 is not linked to a member")));
    }

    @Test
    void shouldReturnUnauthorizedWhenListingOwnPaymentsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/payments"))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).getOwnPayments(any());
    }

    private OwnPaymentsResponse ownPaymentsResponse() {
        OwnPaymentResponse payment = new OwnPaymentResponse(10L, new BigDecimal("15000.00"), Instant.parse("2026-07-03T14:00:00Z"),
                LocalDate.parse("2026-07-01"), PaymentMethod.CASH);

        return new OwnPaymentsResponse(LocalDate.parse("2026-07-01"), 12, List.of(payment));
    }

    private RequestPostProcessor as(AuthenticatedUser principal) {
        String roleAuthority = "ROLE_" + principal.role().name();

        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(roleAuthority))));
    }
}
