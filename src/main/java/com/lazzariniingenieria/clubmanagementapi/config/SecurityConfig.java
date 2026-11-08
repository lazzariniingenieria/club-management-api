package com.lazzariniingenieria.clubmanagementapi.config;

import com.lazzariniingenieria.clubmanagementapi.security.JwtAuthenticationFilter;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AccountSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // "no-store" would make ShallowEtagHeaderFilter skip every response; "private, no-cache" still keeps shared caches
    // out and forces revalidation, which is what the ETag answers.
    private static final String REVALIDATE_PRIVATELY = "private, no-cache";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, AccountSessionService accountSessionService)
            throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers
                .cacheControl(cacheControl -> cacheControl.disable())
                .addHeaderWriter(new StaticHeadersWriter(HttpHeaders.CACHE_CONTROL, REVALIDATE_PRIVATELY)));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/auth/me/password").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/admins/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/members/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/family-groups/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/payments/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/clubs/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .anyRequest().authenticated());
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, accountSessionService);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
