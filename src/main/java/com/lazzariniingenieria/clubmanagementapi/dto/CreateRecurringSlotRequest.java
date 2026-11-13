package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateRecurringSlotRequest(@NotNull(message = "courtId is required")
                                         Long courtId,

                                         Long memberId,

                                         @Size(max = 100, message = "description must be at most 100 characters")
                                         String description,

                                         @NotNull(message = "dayOfWeek is required")
                                         DayOfWeek dayOfWeek,

                                         @NotNull(message = "startTime is required")
                                         LocalTime startTime,

                                         @NotNull(message = "endTime is required")
                                         LocalTime endTime,

                                         @NotNull(message = "validFrom is required")
                                         LocalDate validFrom,

                                         @NotNull(message = "validUntil is required")
                                         LocalDate validUntil) {
}
