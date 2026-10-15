package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import java.time.Instant;
import java.time.LocalDate;

public record MemberResponse(Long id, String firstName, String lastName, String dni, String phone, String email,
        Long familyGroupId, LocalDate joinedAt, MemberStatus status, Instant createdAt, Instant updatedAt,
        Long createdByUserId, Long updatedByUserId) {
}
