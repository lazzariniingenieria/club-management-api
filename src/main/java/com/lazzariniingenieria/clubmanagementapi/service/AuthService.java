package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Optional<UserAccount> userAccount = userAccountRepository.findByClubIdAndDni(request.clubId(),
                request.dni());

        if (userAccount.isEmpty()) {
            throw loginRejected(request);
        }

        UserAccount user = userAccount.get();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches) {
            throw loginRejected(request);
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, user.getRole(), user.getMemberId());
    }

    private InvalidCredentialsException loginRejected(LoginRequest request) {
        log.warn("Login rejected for clubId={}, dni={}", request.clubId(), request.dni());

        return new InvalidCredentialsException();
    }
}
