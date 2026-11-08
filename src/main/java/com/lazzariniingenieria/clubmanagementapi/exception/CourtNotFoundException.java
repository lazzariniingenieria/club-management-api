package com.lazzariniingenieria.clubmanagementapi.exception;

public class CourtNotFoundException extends RuntimeException {

    public CourtNotFoundException(Long courtId) {
        super("Court " + courtId + " not found");
    }
}
