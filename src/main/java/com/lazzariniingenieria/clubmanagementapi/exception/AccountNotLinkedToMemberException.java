package com.lazzariniingenieria.clubmanagementapi.exception;

public class AccountNotLinkedToMemberException extends RuntimeException {

    public AccountNotLinkedToMemberException(Long userAccountId) {
        super("account " + userAccountId + " is not linked to a member");
    }
}
