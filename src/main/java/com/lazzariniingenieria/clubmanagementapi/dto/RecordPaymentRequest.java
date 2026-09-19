package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecordPaymentRequest(@NotNull(message = "memberId is required") Long memberId,

                                    Long paidByMemberId,

                                    @NotNull(message = "amount is required")
                                    @Positive(message = "amount must be greater than zero")
                                    BigDecimal amount,

                                    @NotEmpty(message = "periodsCovered must contain at least one period")
                                    List<@NotNull LocalDate> periodsCovered,

                                    @NotNull(message = "paymentMethod is required")
                                    PaymentMethod paymentMethod) {
}
