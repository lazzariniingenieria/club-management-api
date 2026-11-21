package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OwnPaymentResponse(Long id, BigDecimal amount, Instant paidAt, LocalDate periodCovered, PaymentMethod paymentMethod) {
}
