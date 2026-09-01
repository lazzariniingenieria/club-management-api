package com.lazzariniingenieria.clubmanagementapi.exception;

public class FamilyGroupNotFoundException extends RuntimeException {

    public FamilyGroupNotFoundException(Long familyGroupId) {
        super("Family group " + familyGroupId + " not found");
    }
}
