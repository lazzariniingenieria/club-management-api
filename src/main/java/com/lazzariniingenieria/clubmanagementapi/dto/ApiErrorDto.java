package com.lazzariniingenieria.clubmanagementapi.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorDto(Instant timestamp, int status, String error, String message, List<String> details) {
}
