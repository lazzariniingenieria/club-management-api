package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;

public record LoginResponse(String accessToken, Long userAccountId, UserRole role, Long memberId) {
}
