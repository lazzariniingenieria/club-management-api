package com.lazzariniingenieria.clubmanagementapi.exception;

public class CourtBlockNotFoundException extends RuntimeException {

    public CourtBlockNotFoundException(Long courtBlockId) {
        super("Court block " + courtBlockId + " not found");
    }
}
