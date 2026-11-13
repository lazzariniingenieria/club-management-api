package com.lazzariniingenieria.clubmanagementapi.exception;

public class RecurringSlotNotFoundException extends RuntimeException {

    public RecurringSlotNotFoundException(Long recurringSlotId) {
        super("Recurring slot " + recurringSlotId + " not found");
    }
}
