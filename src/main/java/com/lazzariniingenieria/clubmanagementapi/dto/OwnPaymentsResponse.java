package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.LocalDate;
import java.util.List;

public record OwnPaymentsResponse(LocalDate lastPeriodCovered, long daysOverdue, List<OwnPaymentResponse> payments) {
}
