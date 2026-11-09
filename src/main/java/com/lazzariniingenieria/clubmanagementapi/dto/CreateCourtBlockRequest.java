package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateCourtBlockRequest(@NotNull(message = "startsAt is required")
                                      LocalDateTime startsAt,

                                      @NotNull(message = "endsAt is required")
                                      LocalDateTime endsAt,

                                      @Size(max = 150, message = "reason must be at most 150 characters")
                                      String reason) {
}
