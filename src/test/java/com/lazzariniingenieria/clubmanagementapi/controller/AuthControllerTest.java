package com.lazzariniingenieria.clubmanagementapi.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lazzariniingenieria.clubmanagementapi.config.SecurityConfig;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginRequestDto;
import com.lazzariniingenieria.clubmanagementapi.dto.LoginResponseDto;
import com.lazzariniingenieria.clubmanagementapi.dto.UserSummaryDto;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.AccountDisabledException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCredentialsException;
import com.lazzariniingenieria.clubmanagementapi.repository.UserAccountRepository;
import com.lazzariniingenieria.clubmanagementapi.security.JwtService;
import com.lazzariniingenieria.clubmanagementapi.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldReturnTokenWhenCredentialsAreValid() throws Exception {
        LoginResponseDto response = new LoginResponseDto("token-123", "Bearer", 3600L,
                new UserSummaryDto(1L, "30111222", UserRole.MEMBER, null, 1L));
        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("30111222", "s3cr3t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("token-123")))
                .andExpect(jsonPath("$.user.dni", is("30111222")));
    }

    @Test
    void shouldReturnBadRequestWhenDniIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("", "s3cr3t"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authService.login(any(LoginRequestDto.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("30111222", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenAccountIsDisabled() throws Exception {
        when(authService.login(any(LoginRequestDto.class))).thenThrow(new AccountDisabledException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("30111222", "s3cr3t"))))
                .andExpect(status().isForbidden());
    }
}
