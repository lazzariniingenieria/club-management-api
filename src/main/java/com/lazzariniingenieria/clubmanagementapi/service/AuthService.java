package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequestDto;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponseDto;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.exception.AccountDisabledException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.mapper.UserAccountMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAccountMapper userAccountMapper;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, UserAccountMapper userAccountMapper) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userAccountMapper = userAccountMapper;
    }

    public LoginResponseDto login(LoginRequestDto request) {
        UserAccount user = userAccountRepository.findByDni(request.dni())
                .orElseThrow(() -> loginRejected(request.dni(), "dni not found"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw loginRejected(request.dni(), "password mismatch");
        }

        if (!user.isActive()) {
            log.warn("Login rejected for dni={}: account disabled", request.dni());
            throw new AccountDisabledException();
        }

        String token = jwtService.generateToken(user);
        return new LoginResponseDto(token, "Bearer", jwtService.getExpirationSeconds(),
                userAccountMapper.toSummaryDto(user));
    }

    private InvalidCredentialsException loginRejected(String dni, String reason) {
        log.warn("Login rejected for dni={}: {}", dni, reason);
        return new InvalidCredentialsException();
    }
}
