package com.lazzariniingenieria.clubmanagementapi.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid dni or password");
    }
}
