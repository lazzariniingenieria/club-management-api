package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RecordPaymentBatchRequest(@NotEmpty(message = "payments must contain at least one payment")
                                        List<@Valid @NotNull(message = "payments must not contain null entries")
                                                RecordPaymentRequest> payments) {
}
