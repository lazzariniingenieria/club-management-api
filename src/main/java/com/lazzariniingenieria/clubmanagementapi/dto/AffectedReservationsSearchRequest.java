package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record AffectedReservationsSearchRequest(@NotNull(message = "startsAt is required")
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime startsAt,

                                                @NotNull(message = "endsAt is required")
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime endsAt) {
}
