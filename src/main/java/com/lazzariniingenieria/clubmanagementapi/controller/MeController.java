package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.OwnPaymentsResponse;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    public ResponseEntity<OwnPaymentsResponse> getOwnPayments(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        OwnPaymentsResponse response = paymentService.getOwnPayments(currentUser);

        return ResponseEntity.ok(response);
    }
}
