package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.MemberDelinquencyResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.PaymentSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentBatchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecordPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.VoidPaymentRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<List<PaymentResponse>> record(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                          @Valid @RequestBody RecordPaymentRequest request) {
        List<PaymentResponse> response = paymentService.recordPayment(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<PaymentResponse>> recordBatch(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                               @Valid @RequestBody RecordPaymentBatchRequest request) {
        List<PaymentResponse> response = paymentService.recordPaymentBatch(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> search(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @ModelAttribute PaymentSearchRequest filter) {
        List<PaymentResponse> response = paymentService.searchPayments(currentUser.clubId(), filter);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/members/{memberId}")
    public ResponseEntity<List<PaymentResponse>> listForMember(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                @PathVariable Long memberId,
                                                                @RequestParam(defaultValue = "false") boolean includeVoided) {
        List<PaymentResponse> response = paymentService.listPaymentsForMember(currentUser.clubId(), memberId, includeVoided);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{paymentId}/void")
    public ResponseEntity<PaymentResponse> voidPayment(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                       @PathVariable Long paymentId,
                                                       @Valid @RequestBody(required = false) VoidPaymentRequest request) {
        PaymentResponse response = paymentService.voidPayment(currentUser, paymentId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/delinquency")
    public ResponseEntity<List<MemberDelinquencyResponse>> listDelinquency(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<MemberDelinquencyResponse> response = paymentService.listMemberDelinquency(currentUser.clubId());

        return ResponseEntity.ok(response);
    }
}
