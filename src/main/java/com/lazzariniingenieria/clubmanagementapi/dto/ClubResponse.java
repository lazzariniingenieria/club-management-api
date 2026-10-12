package com.lazzariniingenieria.clubmanagementapi.dto;

import java.math.BigDecimal;

public record ClubResponse(Long id, String name, BigDecimal monthlyFeeAmount) {
}
