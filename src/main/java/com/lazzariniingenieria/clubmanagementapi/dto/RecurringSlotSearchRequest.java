package com.lazzariniingenieria.clubmanagementapi.dto;

import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;

public record RecurringSlotSearchRequest(Long courtId, Long memberId, RecurringSlotState state) {
}
