package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.AffectedReservationsSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtBlockRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.CourtBlockService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courts/{courtId}/blocks")
@RequiredArgsConstructor
public class CourtBlockController {

    private final CourtBlockService courtBlockService;

    @PostMapping
    public ResponseEntity<CourtBlockResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                     @PathVariable Long courtId,
                                                     @Valid @RequestBody CreateCourtBlockRequest request) {
        CourtBlockResponse response = courtBlockService.createCourtBlock(currentUser, courtId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CourtBlockResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                         @PathVariable Long courtId,
                                                         @ModelAttribute CourtBlockSearchRequest filter) {
        List<CourtBlockResponse> response = courtBlockService.listCourtBlocks(currentUser.clubId(), courtId, filter);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/affected-reservations")
    public ResponseEntity<List<ReservationResponse>> listAffected(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                  @PathVariable Long courtId,
                                                                  @Valid @ModelAttribute AffectedReservationsSearchRequest filter) {
        List<ReservationResponse> response = courtBlockService.listAffectedReservations(currentUser.clubId(), courtId, filter);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{courtBlockId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable Long courtId,
                                       @PathVariable Long courtBlockId) {
        courtBlockService.deleteCourtBlock(currentUser, courtId, courtBlockId);

        return ResponseEntity.noContent().build();
    }
}
