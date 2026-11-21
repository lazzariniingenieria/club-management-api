package com.lazzariniingenieria.clubmanagementapi.exception;

public class InactiveAdminCannotBeLinkedException extends RuntimeException {

    public InactiveAdminCannotBeLinkedException(Long adminId) {
        super("admin " + adminId + " is inactive: reactivate it before linking it to a member");
    }
}
