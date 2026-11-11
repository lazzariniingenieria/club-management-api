package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record CourtBlockResponse(Long id, Long courtId, LocalDateTime startsAt, LocalDateTime endsAt, String reason, Instant createdAt,
        Long createdByUserId) {
}
