package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record CreateCourtRequest(@NotBlank(message = "name is required")
                                 @Size(max = 100, message = "name must be at most 100 characters")
                                 String name,

                                 @Size(max = 50, message = "type must be at most 50 characters")
                                 String type,

                                 @NotNull(message = "openingTime is required")
                                 LocalTime openingTime,

                                 @NotNull(message = "closingTime is required")
                                 LocalTime closingTime) {
}
