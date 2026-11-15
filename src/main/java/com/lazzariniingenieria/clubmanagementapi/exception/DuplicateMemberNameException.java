package com.lazzariniingenieria.clubmanagementapi.exception;

public class DuplicateMemberNameException extends RuntimeException {

    public DuplicateMemberNameException(String firstName, String lastName) {
        super("a member named " + firstName + " " + lastName + " already exists in this club");
    }
}
