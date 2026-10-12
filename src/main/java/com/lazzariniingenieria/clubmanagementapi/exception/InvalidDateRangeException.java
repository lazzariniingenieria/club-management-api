package com.lazzariniingenieria.clubmanagementapi.exception;

import java.time.LocalDate;

public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        super("from " + from + " must not be after to " + to);
    }
}
