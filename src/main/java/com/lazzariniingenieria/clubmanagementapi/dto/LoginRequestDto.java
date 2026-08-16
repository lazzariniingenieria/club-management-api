package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(@NotBlank(message = "dni is required") String dni,
                               @NotBlank(message = "password is required") String password) {
}
