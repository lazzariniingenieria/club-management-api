package com.lazzariniingenieria.clubmanagementapi.exception;

public class ClubNotFoundException extends RuntimeException {

    public ClubNotFoundException(Long clubId) {
        super("Club " + clubId + " not found");
    }
}
