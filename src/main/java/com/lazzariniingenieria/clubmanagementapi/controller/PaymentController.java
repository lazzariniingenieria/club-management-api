package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/payments")
    public ResponseEntity<List<PaymentResponse>> record(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                          @Valid @RequestBody RecordPaymentRequest request) {
        List<PaymentResponse> response = paymentService.recordPayment(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/members/{memberId}/payments")
    public ResponseEntity<List<PaymentResponse>> listForMember(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                @PathVariable Long memberId) {
        List<PaymentResponse> response = paymentService.listPaymentsForMember(currentUser.clubId(), memberId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/payments/delinquency")
    public ResponseEntity<List<MemberDelinquencyResponse>> listDelinquency(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<MemberDelinquencyResponse> response = paymentService.listMemberDelinquency(currentUser.clubId());

        return ResponseEntity.ok(response);
    }
}
