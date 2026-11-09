package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ReservationSearchRequest(@NotNull(message = "date is required")
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                       LocalDate date,

                                       Long courtId) {
}
