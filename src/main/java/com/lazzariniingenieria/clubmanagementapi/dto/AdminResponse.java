package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.Instant;

public record AdminResponse(Long id, String dni, String email, Long memberId, boolean active, Instant createdAt,
        Instant updatedAt, Long createdByUserId, Long updatedByUserId) {
}
