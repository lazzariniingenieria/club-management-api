package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import java.time.Instant;

public record AdminResponse(Long id, String dni, String email, Long memberId, UserRole role, boolean active, Instant createdAt,
        Instant updatedAt, Long createdByUserId, Long updatedByUserId) {
}
