package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotNull(message = "clubId is required") Long clubId,

                            @NotBlank(message = "dni is required")
                            @Size(max = 20, message = "dni must be at most 20 characters")
                            String dni,

                            @NotBlank(message = "password is required") String password) {
}
