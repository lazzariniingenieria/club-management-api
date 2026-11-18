package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import java.time.Instant;

public record MemberAccountResponse(Long id, Long memberId, String dni, UserRole role, boolean active, Instant createdAt,
        Instant updatedAt, Long createdByUserId, Long updatedByUserId) {
}
