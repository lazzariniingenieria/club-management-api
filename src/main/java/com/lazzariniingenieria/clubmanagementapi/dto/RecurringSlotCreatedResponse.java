package com.lazzariniingenieria.clubmanagementapi.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record RecurringSlotCreatedResponse(@JsonUnwrapped RecurringSlotResponse recurringSlot, Long memberDaysOverdue) {
}
