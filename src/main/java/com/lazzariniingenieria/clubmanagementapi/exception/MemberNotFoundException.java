package com.lazzariniingenieria.clubmanagementapi.exception;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(Long memberId) {
        super("Member " + memberId + " not found");
    }
}
