package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;

public record PromoteMemberToAdminRequest(@NotNull(message = "memberId is required") Long memberId) {
}
