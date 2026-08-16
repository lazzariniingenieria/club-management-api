package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;

public record LoginResponseDto(String accessToken, UserRole role, Long memberId) {
}
