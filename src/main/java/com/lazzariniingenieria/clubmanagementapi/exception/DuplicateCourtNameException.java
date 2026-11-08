package com.lazzariniingenieria.clubmanagementapi.exception;

public class DuplicateCourtNameException extends RuntimeException {

    public DuplicateCourtNameException(String name) {
        super("court name " + name + " is already in use in this club");
    }
}
