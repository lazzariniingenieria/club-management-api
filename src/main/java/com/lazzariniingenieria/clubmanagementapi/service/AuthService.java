package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequestDto;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponseDto;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
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

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponseDto login(LoginRequestDto request) {
        UserAccount user = userAccountRepository.findByClubIdAndDni(request.clubId(), request.dni())
                .orElseThrow(() -> loginRejected(request));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw loginRejected(request);
        }

        String token = jwtService.generateToken(user);

        return new LoginResponseDto(token, user.getRole(), user.getMemberId());
    }

    private InvalidCredentialsException loginRejected(LoginRequestDto request) {
        log.warn("Login rejected for clubId={}, dni={}", request.clubId(), request.dni());

        return new InvalidCredentialsException();
    }
}
