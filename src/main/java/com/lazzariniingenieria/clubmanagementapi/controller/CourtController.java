package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.CourtService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    @PostMapping
    public ResponseEntity<CourtResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                @Valid @RequestBody CreateCourtRequest request) {
        CourtResponse response = courtService.createCourt(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CourtResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<CourtResponse> response = courtService.listCourts(currentUser.clubId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{courtId}")
    public ResponseEntity<CourtResponse> getOne(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long courtId) {
        CourtResponse response = courtService.getCourt(currentUser.clubId(), courtId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{courtId}")
    public ResponseEntity<CourtResponse> update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                @PathVariable Long courtId,
                                                @Valid @RequestBody UpdateCourtRequest request) {
        CourtResponse response = courtService.updateCourt(currentUser, courtId, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{courtId}/deactivate")
    public ResponseEntity<CourtResponse> deactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long courtId) {
        CourtResponse response = courtService.deactivateCourt(currentUser, courtId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{courtId}/reactivate")
    public ResponseEntity<CourtResponse> reactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long courtId) {
        CourtResponse response = courtService.reactivateCourt(currentUser, courtId);

        return ResponseEntity.ok(response);
    }
}
