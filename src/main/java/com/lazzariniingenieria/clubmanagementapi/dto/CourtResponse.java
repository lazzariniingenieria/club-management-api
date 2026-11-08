package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.Instant;
import java.time.LocalTime;

public record CourtResponse(Long id, String name, String type, LocalTime openingTime, LocalTime closingTime, boolean active,
        Instant createdAt, Instant updatedAt, Long createdByUserId, Long updatedByUserId) {
}
