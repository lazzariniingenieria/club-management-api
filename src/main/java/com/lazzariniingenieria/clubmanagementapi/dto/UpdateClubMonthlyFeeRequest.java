package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateClubMonthlyFeeRequest(@Positive(message = "monthlyFeeAmount must be greater than zero")
                                          @Digits(integer = 8, fraction = 2,
                                                  message = "monthlyFeeAmount must have at most 8 integer digits and 2 decimal places")
                                          BigDecimal monthlyFeeAmount) {
}
