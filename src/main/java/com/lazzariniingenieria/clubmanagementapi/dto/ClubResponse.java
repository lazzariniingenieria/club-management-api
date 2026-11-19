package com.lazzariniingenieria.clubmanagementapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClubResponse(Long id, String name, BigDecimal monthlyFeeAmount, boolean courtsEnabled, boolean memberAppEnabled,
        Instant updatedAt, Long updatedByUserId) {
}
