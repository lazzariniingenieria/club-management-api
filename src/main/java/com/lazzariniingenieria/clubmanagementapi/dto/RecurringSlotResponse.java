package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record RecurringSlotResponse(Long id, Long courtId, Long memberId, String description, DayOfWeek dayOfWeek, LocalTime startTime,
        LocalTime endTime, LocalDate validFrom, LocalDate validUntil, boolean active, RecurringSlotState state, Instant createdAt,
        Long createdByUserId, Instant deactivatedAt, Long deactivatedByUserId) {
}
