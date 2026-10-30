package com.lazzariniingenieria.clubmanagementapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TOKEN = "signed-token";
    private static final Instant ISSUED_AT = Instant.parse("2026-10-24T12:00:00Z");

    @Mock
    private JwtService jwtService;

    @Mock
    private AccountSessionService accountSessionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, accountSessionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotAuthenticateWhenAuthorizationHeaderIsMissing() throws Exception {
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenAuthorizationHeaderDoesNotUseBearerScheme() throws Exception {
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid() throws Exception {
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer " + TOKEN);
        when(jwtService.parseAccessToken(TOKEN)).thenThrow(new JwtException("invalid token"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        verifyNoInteractions(accountSessionService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateWithRoleAuthorityWhenTokenIsValid() throws Exception {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, 10L, UserRole.ADMIN, 5L);
        AccessTokenClaims accessToken = new AccessTokenClaims(authenticatedUser, ISSUED_AT);
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer " + TOKEN);
        when(jwtService.parseAccessToken(TOKEN)).thenReturn(accessToken);
        when(accountSessionService.isSessionValid(accessToken)).thenReturn(true);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(authenticatedUser);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsValidButTheSessionWasCutByDeactivationOrSessionsRevocation() throws Exception {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, 10L, UserRole.ADMIN, 5L);
        AccessTokenClaims accessToken = new AccessTokenClaims(authenticatedUser, ISSUED_AT);
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer " + TOKEN);
        when(jwtService.parseAccessToken(TOKEN)).thenReturn(accessToken);
        when(accountSessionService.isSessionValid(accessToken)).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
