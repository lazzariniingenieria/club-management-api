package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateReservationRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpcomingReservationsRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.ReservationService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                      @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                          @Valid @ModelAttribute ReservationSearchRequest filter) {
        List<ReservationResponse> response = reservationService.listReservations(currentUser.clubId(), filter);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ReservationResponse>> listUpcoming(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                  @Valid @ModelAttribute UpcomingReservationsRequest filter) {
        List<ReservationResponse> response = reservationService.listUpcomingReservations(currentUser.clubId(), filter.courtId());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                      @PathVariable Long reservationId) {
        ReservationResponse response = reservationService.cancelReservation(currentUser, reservationId);

        return ResponseEntity.ok(response);
    }
}
