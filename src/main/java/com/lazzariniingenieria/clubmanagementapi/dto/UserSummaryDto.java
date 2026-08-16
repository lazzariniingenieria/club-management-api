package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;

public record UserSummaryDto(Long id, String dni, UserRole role, Long memberId, Long clubId) {
}
