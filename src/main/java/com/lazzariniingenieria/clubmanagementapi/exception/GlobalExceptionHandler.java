package com.lazzariniingenieria.clubmanagementapi.exception;

import com.lazzariniingenieria.clubmanagementapi.dto.ApiErrorDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidCredentials(InvalidCredentialsException exception) {
        log.warn("Login attempt rejected: {}", exception.getMessage());

        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorDto> handleMalformedRequestBody(HttpMessageNotReadableException exception) {
        log.warn("Rejected request with a malformed body: {}", exception.getMessage());

        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(DuplicateDniException.class)
    public ResponseEntity<ApiErrorDto> handleDuplicateDni(DuplicateDniException exception) {
        log.warn("Rejected request due to duplicate dni: {}", exception.getMessage());

        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleAdminNotFound(AdminNotFoundException exception) {
        log.warn("Admin lookup failed: {}", exception.getMessage());

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleMemberNotFound(MemberNotFoundException exception) {
        log.warn("Member lookup failed: {}", exception.getMessage());

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(FamilyGroupNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleFamilyGroupNotFound(FamilyGroupNotFoundException exception) {
        log.warn("Family group lookup failed: {}", exception.getMessage());

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDto> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Rejected request due to a database constraint violation", exception);

        return buildResponse(HttpStatus.CONFLICT, "Request violates a database constraint", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        List<String> details = new ArrayList<>();

        for (FieldError fieldError : fieldErrors) {
            String fieldName = fieldError.getField();
            String errorMessage = fieldError.getDefaultMessage();
            String detail = fieldName + ": " + errorMessage;
            details.add(detail);
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpectedError(Exception exception) {
        log.error("Unexpected error while handling request", exception);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", List.of());
    }

    private ResponseEntity<ApiErrorDto> buildResponse(HttpStatus status, String message, List<String> details) {
        ApiErrorDto body = new ApiErrorDto(Instant.now(), status.value(), status.getReasonPhrase(), message, details);

        return ResponseEntity.status(status).body(body);
    }
}
