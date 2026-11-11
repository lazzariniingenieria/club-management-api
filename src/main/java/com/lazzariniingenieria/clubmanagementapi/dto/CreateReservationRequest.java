package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(@NotNull(message = "courtId is required")
                                       Long courtId,

                                       @NotNull(message = "memberId is required")
                                       Long memberId,

                                       @NotNull(message = "date is required")
                                       LocalDate date,

                                       @NotNull(message = "startTime is required")
                                       LocalTime startTime,

                                       @NotNull(message = "endTime is required")
                                       LocalTime endTime) {
}
