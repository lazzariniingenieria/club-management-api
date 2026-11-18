package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMemberAccountRequest(@NotBlank(message = "password is required")
                                          @Size(min = 8, message = "password must be at least 8 characters")
                                          @MaxUtf8Bytes(value = 72, message = "password must be at most 72 bytes (accents count as 2)")
                                          String password) {
}
