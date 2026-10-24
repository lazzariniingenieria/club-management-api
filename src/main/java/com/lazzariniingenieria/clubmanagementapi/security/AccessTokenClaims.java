package com.lazzariniingenieria.clubmanagementapi.security;

import java.time.Instant;

public record AccessTokenClaims(AuthenticatedUser authenticatedUser, Instant issuedAt) {
}
