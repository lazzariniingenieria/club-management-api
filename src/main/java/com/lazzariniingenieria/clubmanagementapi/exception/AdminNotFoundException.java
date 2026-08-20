package com.lazzariniingenieria.clubmanagementapi.exception;

public class AdminNotFoundException extends RuntimeException {

    public AdminNotFoundException(Long adminId) {
        super("Admin " + adminId + " not found");
    }
}
