package com.lazzariniingenieria.clubmanagementapi.dto;

public record LoginResponseDto(String accessToken, String tokenType, long expiresInSeconds, UserSummaryDto user) {
}
