package com.lazzariniingenieria.clubmanagementapi.exception;

public class DuplicateDniException extends RuntimeException {

    public DuplicateDniException(String dni) {
        super("dni " + dni + " is already in use in this club");
    }
}
