package com.lazzariniingenieria.clubmanagementapi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record FeeReceiptReportRequest(@NotNull(message = "paidFrom is required")
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                      LocalDate paidFrom,

                                      @NotNull(message = "paidTo is required")
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                      LocalDate paidTo) {
}
