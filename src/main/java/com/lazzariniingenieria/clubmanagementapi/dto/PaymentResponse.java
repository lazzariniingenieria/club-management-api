package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentResponse(Long id, Long memberId, Long paidByMemberId, BigDecimal amount, Instant paidAt,
        LocalDate periodCovered, PaymentMethod paymentMethod, Long recordedByUserId, Instant createdAt) {
}
