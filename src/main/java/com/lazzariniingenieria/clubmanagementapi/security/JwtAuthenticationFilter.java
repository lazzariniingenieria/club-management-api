package com.lazzariniingenieria.clubmanagementapi.security;

import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountSessionService accountSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            authenticateIfValid(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateIfValid(String token) {
        try {
            AccessTokenClaims accessToken = jwtService.parseAccessToken(token);
            authenticateIfSessionIsValid(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Rejected request with an invalid or expired token: {}", exception.getMessage());
        }
    }

    private void authenticateIfSessionIsValid(AccessTokenClaims accessToken) {
        AuthenticatedUser authenticatedUser = accessToken.authenticatedUser();
        boolean sessionValid = accountSessionService.isSessionValid(accessToken);

        if (!sessionValid) {
            log.info("Rejected access token of userAccountId={} in clubId={}: account deactivated or password changed",
                    authenticatedUser.userAccountId(), authenticatedUser.clubId());
            return;
        }

        authenticate(authenticatedUser);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        boolean hasBearerToken = header != null && header.startsWith(BEARER_PREFIX);

        if (!hasBearerToken) {
            return null;
        }

        return header.substring(BEARER_PREFIX.length());
    }

    private void authenticate(AuthenticatedUser authenticatedUser) {
        String roleAuthority = "ROLE_" + authenticatedUser.role().name();
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleAuthority);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of(authority));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
