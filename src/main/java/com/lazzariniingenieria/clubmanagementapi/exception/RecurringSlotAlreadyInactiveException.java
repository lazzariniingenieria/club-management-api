package com.lazzariniingenieria.clubmanagementapi.exception;

public class RecurringSlotAlreadyInactiveException extends RuntimeException {

    public RecurringSlotAlreadyInactiveException(Long recurringSlotId) {
        super("Recurring slot " + recurringSlotId + " is already inactive");
    }
}
