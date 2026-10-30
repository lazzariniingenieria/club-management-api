package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateFamilyGroupRequest(@Size(max = 150, message = "name must be at most 150 characters") String name,
                                       List<@NotNull(message = "memberIds must not contain null values") Long> memberIds) {
}
