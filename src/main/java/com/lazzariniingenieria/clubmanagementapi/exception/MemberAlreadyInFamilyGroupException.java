package com.lazzariniingenieria.clubmanagementapi.exception;

import java.util.List;

public class MemberAlreadyInFamilyGroupException extends RuntimeException {

    public MemberAlreadyInFamilyGroupException(List<Long> memberIds) {
        super("Member(s) " + memberIds + " already belong to a family group");
    }
}
