package com.lazzariniingenieria.clubmanagementapi.report;

import java.math.BigDecimal;

public record FeeReceiptLine(String memberName, BigDecimal amount) {
}
