package com.lazzariniingenieria.clubmanagementapi.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record ReservationCreatedResponse(@JsonUnwrapped ReservationResponse reservation, long memberDaysOverdue) {
}
