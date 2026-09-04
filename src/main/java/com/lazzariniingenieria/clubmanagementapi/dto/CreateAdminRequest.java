package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(@NotBlank(message = "dni is required")
                                  @Size(max = 20, message = "dni must be at most 20 characters")
                                  String dni,

                                  @NotBlank(message = "password is required")
                                  @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
                                  String password,

                                  @Email(message = "email must be a valid address")
                                  @Size(max = 150, message = "email must be at most 150 characters")
                                  String email,

                                  Long memberId) {
}
