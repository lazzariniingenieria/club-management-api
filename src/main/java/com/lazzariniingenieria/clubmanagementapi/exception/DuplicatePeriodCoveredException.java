package com.lazzariniingenieria.clubmanagementapi.exception;

public class DuplicatePeriodCoveredException extends RuntimeException {

    public DuplicatePeriodCoveredException() {
        super("periodsCovered must not contain duplicate dates");
    }
}
