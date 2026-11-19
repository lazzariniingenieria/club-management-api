package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberAccountRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberAccountResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ResetMemberAccountPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.MemberAccountService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/{memberId}/account")
@RequiredArgsConstructor
public class MemberAccountController {

    private final MemberAccountService memberAccountService;

    @GetMapping
    public ResponseEntity<MemberAccountResponse> getOne(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                         @PathVariable Long memberId) {
        Optional<MemberAccountResponse> response = memberAccountService.getAccount(currentUser.clubId(), memberId);

        if (response.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PostMapping
    public ResponseEntity<MemberAccountResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                         @PathVariable Long memberId,
                                                         @Valid @RequestBody CreateMemberAccountRequest request) {
        MemberAccountResponse response = memberAccountService.createAccount(currentUser, memberId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/password")
    public ResponseEntity<MemberAccountResponse> resetPassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                @PathVariable Long memberId,
                                                                @Valid @RequestBody ResetMemberAccountPasswordRequest request) {
        MemberAccountResponse response = memberAccountService.resetPassword(currentUser, memberId, request);

        return ResponseEntity.ok(response);
    }
}
