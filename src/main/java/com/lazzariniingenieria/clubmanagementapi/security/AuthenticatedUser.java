package com.lazzariniingenieria.clubmanagementapi.security;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;

public record AuthenticatedUser(Long userAccountId, Long clubId, UserRole role, Long memberId) {
}
