package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;

public record LoginResponse(String accessToken, String refreshToken, Long expiresIn, Long userAccountId, UserRole role,
                             Long memberId) {
}
