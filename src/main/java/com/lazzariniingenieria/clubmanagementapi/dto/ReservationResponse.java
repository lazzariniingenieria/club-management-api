package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationResponse(Long id, Long courtId, Long memberId, ReservationSource source, Long recurringSlotId,
        String description, LocalDate date, LocalTime startTime, LocalTime endTime, ReservationStatus status, Instant createdAt,
        Long createdByUserId, Instant cancelledAt, Long cancelledByUserId) {
}
