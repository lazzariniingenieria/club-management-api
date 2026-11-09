package com.lazzariniingenieria.clubmanagementapi.exception;

public class ReservationSlotTakenException extends RuntimeException {

    public ReservationSlotTakenException(String message) {
        super(message);
    }
}
