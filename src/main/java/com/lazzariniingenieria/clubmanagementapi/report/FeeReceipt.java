package com.lazzariniingenieria.clubmanagementapi.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FeeReceipt(LocalDate periodCovered, List<FeeReceiptLine> lines, BigDecimal totalAmount) {
}
