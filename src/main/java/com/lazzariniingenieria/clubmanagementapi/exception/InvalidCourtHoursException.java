package com.lazzariniingenieria.clubmanagementapi.exception;

import java.time.LocalTime;

public class InvalidCourtHoursException extends RuntimeException {

    public InvalidCourtHoursException(LocalTime openingTime, LocalTime closingTime) {
        super("closingTime " + closingTime + " must be after openingTime " + openingTime);
    }
}
