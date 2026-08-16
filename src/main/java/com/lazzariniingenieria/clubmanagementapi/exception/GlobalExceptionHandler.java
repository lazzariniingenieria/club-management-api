package com.lazzariniingenieria.clubmanagementapi.exception;

import com.lazzariniingenieria.clubmanagementapi.dto.ApiErrorDto;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidCredentials(InvalidCredentialsException exception) {
        log.warn("Login attempt rejected: {}", exception.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorDto> handleAccountDisabled(AccountDisabledException exception) {
        log.warn("Login attempt rejected: {}", exception.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    private ResponseEntity<ApiErrorDto> buildResponse(HttpStatus status, String message, List<String> details) {
        ApiErrorDto body = new ApiErrorDto(Instant.now(), status.value(), status.getReasonPhrase(), message, details);
        return ResponseEntity.status(status).body(body);
    }
}
