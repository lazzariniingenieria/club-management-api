package com.lazzariniingenieria.clubmanagementapi.exception;

public class MemberAccountNotFoundException extends RuntimeException {

    public MemberAccountNotFoundException(Long memberId) {
        super("member " + memberId + " has no member account");
    }
}
