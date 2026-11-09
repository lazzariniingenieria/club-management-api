package com.lazzariniingenieria.clubmanagementapi.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(Long reservationId) {
        super("Reservation " + reservationId + " not found");
    }
}
