package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(@NotBlank(message = "firstName is required")
                                   @Size(max = 100, message = "firstName must be at most 100 characters")
                                   String firstName,

                                   @NotBlank(message = "lastName is required")
                                   @Size(max = 100, message = "lastName must be at most 100 characters")
                                   String lastName,

                                   @NotBlank(message = "dni is required")
                                   @Size(max = 20, message = "dni must be at most 20 characters")
                                   String dni,

                                   @Size(max = 30, message = "phone must be at most 30 characters")
                                   String phone,

                                   @Email(message = "email must be a valid address")
                                   @Size(max = 150, message = "email must be at most 150 characters")
                                   String email,

                                   Long familyGroupId) {
}
