package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RenewRecurringSlotRequest(@NotNull(message = "validFrom is required")
                                        LocalDate validFrom,

                                        @NotNull(message = "validUntil is required")
                                        LocalDate validUntil) {
}
