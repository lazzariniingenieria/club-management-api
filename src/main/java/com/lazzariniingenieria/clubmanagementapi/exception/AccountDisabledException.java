package com.lazzariniingenieria.clubmanagementapi.exception;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("This account is disabled");
    }
}
