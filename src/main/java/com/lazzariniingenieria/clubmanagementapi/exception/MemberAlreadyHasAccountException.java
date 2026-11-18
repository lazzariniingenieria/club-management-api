package com.lazzariniingenieria.clubmanagementapi.exception;

public class MemberAlreadyHasAccountException extends RuntimeException {

    public MemberAlreadyHasAccountException(Long memberId) {
        super("member " + memberId + " already has an account");
    }
}
