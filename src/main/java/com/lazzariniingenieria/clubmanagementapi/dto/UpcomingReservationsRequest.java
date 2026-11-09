package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;

public record UpcomingReservationsRequest(@NotNull(message = "courtId is required")
                                          Long courtId) {
}
