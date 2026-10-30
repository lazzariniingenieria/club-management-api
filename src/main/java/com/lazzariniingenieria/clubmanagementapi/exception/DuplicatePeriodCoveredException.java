package com.lazzariniingenieria.clubmanagementapi.exception;

public class DuplicatePeriodCoveredException extends RuntimeException {

    public DuplicatePeriodCoveredException(Long memberId) {
        super("periodsCovered of memberId " + memberId + " must not contain duplicate dates");
    }
}
