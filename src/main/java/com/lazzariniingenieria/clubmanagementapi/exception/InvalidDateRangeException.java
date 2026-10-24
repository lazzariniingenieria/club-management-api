package com.lazzariniingenieria.clubmanagementapi.exception;

import java.time.LocalDate;

public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        this("from " + from + " must not be after to " + to);
    }

    public InvalidDateRangeException(String message) {
        super(message);
    }
}
