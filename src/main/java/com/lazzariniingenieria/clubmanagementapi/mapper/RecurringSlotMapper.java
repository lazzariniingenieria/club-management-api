package com.lazzariniingenieria.clubmanagementapi.mapper;

import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecurringSlotMapper {

    RecurringSlotResponse toResponse(RecurringSlot recurringSlot, RecurringSlotState state);
}
