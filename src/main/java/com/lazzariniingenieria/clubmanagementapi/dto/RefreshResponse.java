package com.lazzariniingenieria.clubmanagementapi.dto;

public record RefreshResponse(String accessToken, String refreshToken, Long expiresIn) {
}
