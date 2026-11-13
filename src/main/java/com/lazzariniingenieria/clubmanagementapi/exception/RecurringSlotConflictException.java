package com.lazzariniingenieria.clubmanagementapi.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class RecurringSlotConflictException extends RuntimeException {

    private final List<String> conflicts;

    public RecurringSlotConflictException(List<String> conflicts) {
        super("recurring slot conflicts with existing reservations or court blocks");
        this.conflicts = List.copyOf(conflicts);
    }
}
