package com.lazzariniingenieria.clubmanagementapi.exception;

public class ReservationAlreadyCancelledException extends RuntimeException {

    public ReservationAlreadyCancelledException(Long reservationId) {
        super("Reservation " + reservationId + " is already cancelled");
    }
}
