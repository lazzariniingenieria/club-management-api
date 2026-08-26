package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.Instant;

public record FamilyGroupResponse(Long id, String name, Instant createdAt) {
}
