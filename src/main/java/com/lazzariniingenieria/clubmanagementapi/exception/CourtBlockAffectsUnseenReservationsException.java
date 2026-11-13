package com.lazzariniingenieria.clubmanagementapi.exception;

import java.util.List;
import lombok.Getter;

@Getter
public class CourtBlockAffectsUnseenReservationsException extends RuntimeException {

    private final List<String> unseenReservations;

    public CourtBlockAffectsUnseenReservationsException(List<String> unseenReservations) {
        super("court block would cancel reservations not shown to the admin");
        this.unseenReservations = List.copyOf(unseenReservations);
    }
}
