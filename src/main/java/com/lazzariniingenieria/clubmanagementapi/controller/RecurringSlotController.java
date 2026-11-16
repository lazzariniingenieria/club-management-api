package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RenewRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.RecurringSlotService;
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
@RequestMapping("/api/recurring-slots")
@RequiredArgsConstructor
public class RecurringSlotController {

    private final RecurringSlotService recurringSlotService;

    @PostMapping
    public ResponseEntity<RecurringSlotResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @Valid @RequestBody CreateRecurringSlotRequest request) {
        RecurringSlotResponse response = recurringSlotService.createRecurringSlot(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RecurringSlotResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                            @ModelAttribute RecurringSlotSearchRequest filter) {
        List<RecurringSlotResponse> response = recurringSlotService.listRecurringSlots(currentUser.clubId(), filter);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{recurringSlotId}")
    public ResponseEntity<RecurringSlotResponse> get(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                     @PathVariable Long recurringSlotId) {
        RecurringSlotResponse response = recurringSlotService.getRecurringSlot(currentUser.clubId(), recurringSlotId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{recurringSlotId}/deactivate")
    public ResponseEntity<RecurringSlotResponse> deactivate(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                            @PathVariable Long recurringSlotId) {
        RecurringSlotResponse response = recurringSlotService.deactivateRecurringSlot(currentUser, recurringSlotId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{recurringSlotId}/renew")
    public ResponseEntity<RecurringSlotResponse> renew(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                       @PathVariable Long recurringSlotId,
                                                       @Valid @RequestBody RenewRecurringSlotRequest request) {
        RecurringSlotResponse response = recurringSlotService.renewRecurringSlot(currentUser, recurringSlotId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
