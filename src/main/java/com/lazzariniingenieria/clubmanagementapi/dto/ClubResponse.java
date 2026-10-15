package com.lazzariniingenieria.clubmanagementapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClubResponse(Long id, String name, BigDecimal monthlyFeeAmount, Instant updatedAt, Long updatedByUserId) {
}
