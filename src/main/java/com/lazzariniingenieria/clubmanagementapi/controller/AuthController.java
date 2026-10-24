package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.ChangeOwnPasswordRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RefreshResponse;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                  @Valid @RequestBody ChangeOwnPasswordRequest request) {
        authService.changeOwnPassword(currentUser, request);

        return ResponseEntity.noContent().build();
    }
}
