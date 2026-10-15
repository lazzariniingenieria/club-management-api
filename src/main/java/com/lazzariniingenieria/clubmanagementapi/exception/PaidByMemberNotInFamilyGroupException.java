package com.lazzariniingenieria.clubmanagementapi.exception;

public class PaidByMemberNotInFamilyGroupException extends RuntimeException {

    public PaidByMemberNotInFamilyGroupException(Long paidByMemberId, Long memberId) {
        super("Member " + paidByMemberId + " is not in the same family group as member " + memberId);
    }
}
