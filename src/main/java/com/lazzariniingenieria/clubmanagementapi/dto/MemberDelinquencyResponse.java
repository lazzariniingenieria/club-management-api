package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.LocalDate;

public record MemberDelinquencyResponse(Long memberId, String firstName, String lastName, LocalDate lastPeriodCovered,
        long daysOverdue) {
}
