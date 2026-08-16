package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequestDto(@NotNull(message = "clubId is required") Long clubId,
                               @NotBlank(message = "nationalId is required") String nationalId,
                               @NotBlank(message = "password is required") String password) {
}
