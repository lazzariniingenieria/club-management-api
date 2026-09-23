package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAdminPasswordRequest(@NotBlank(message = "newPassword is required")
                                         @Size(min = 8, max = 100, message = "newPassword must be between 8 and 100 characters")
                                         String newPassword) {
}
