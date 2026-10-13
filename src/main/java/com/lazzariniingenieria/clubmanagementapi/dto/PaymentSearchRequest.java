package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.PaymentMethod;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

// includeVoided is a Boolean, not a boolean: query params bind through the record constructor, which rejects a missing
// value for a primitive instead of defaulting it to false. A missing value means "exclude voided payments".
public record PaymentSearchRequest(Long memberId,
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   PaymentMethod paymentMethod,
                                   Boolean includeVoided) {
}
