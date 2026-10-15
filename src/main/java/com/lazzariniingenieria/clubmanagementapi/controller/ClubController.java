package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.ClubResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateClubMonthlyFeeRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.ClubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping("/me")
    public ResponseEntity<ClubResponse> getCurrent(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        ClubResponse response = clubService.getClub(currentUser.clubId());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<ClubResponse> updateMonthlyFee(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                         @Valid @RequestBody UpdateClubMonthlyFeeRequest request) {
        ClubResponse response = clubService.updateMonthlyFee(currentUser, request);

        return ResponseEntity.ok(response);
    }
}
