package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.Size;

public record CreateFamilyGroupRequest(@Size(max = 150, message = "name must be at most 150 characters") String name) {
}
