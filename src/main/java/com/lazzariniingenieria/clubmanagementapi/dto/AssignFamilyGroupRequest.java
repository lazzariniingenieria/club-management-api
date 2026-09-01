package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;

public record AssignFamilyGroupRequest(@NotNull(message = "familyGroupId is required") Long familyGroupId) {
}
