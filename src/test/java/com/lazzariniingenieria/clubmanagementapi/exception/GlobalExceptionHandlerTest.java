package com.lazzariniingenieria.clubmanagementapi.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.ApiErrorDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturnUnauthorizedWithExceptionMessageWhenCredentialsAreInvalid() {
        InvalidCredentialsException exception = new InvalidCredentialsException();

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleInvalidCredentials(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.error()).isEqualTo("Unauthorized");
        assertThat(body.message()).isEqualTo("Invalid dni or password");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformed() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error", mock(HttpInputMessage.class));

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleMalformedRequestBody(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.message()).isEqualTo("Malformed request body");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnBadRequestWithFieldDetailsWhenValidationFails() {
        FieldError dniError = new FieldError("loginRequest", "dni", "must not be blank");
        FieldError clubIdError = new FieldError("loginRequest", "clubId", "must not be null");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(dniError, clubIdError));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleValidation(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.details()).containsExactly("dni: must not be blank", "clubId: must not be null");
    }

    @Test
    void shouldReturnEmptyDetailsWhenValidationFailsWithNoFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleValidation(exception);

        assertThat(response.getBody().details()).isEmpty();
    }

    @Test
    void shouldReturnConflictWithExceptionMessageWhenDniIsDuplicated() {
        DuplicateDniException exception = new DuplicateDniException("30111222");

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleDuplicateDni(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.message()).isEqualTo("dni 30111222 is already in use in this club");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWithExceptionMessageWhenAdminDoesNotExist() {
        AdminNotFoundException exception = new AdminNotFoundException(42L);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleAdminNotFound(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Admin 42 not found");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWithExceptionMessageWhenMemberDoesNotExist() {
        MemberNotFoundException exception = new MemberNotFoundException(42L);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleMemberNotFound(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Member 42 not found");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWithExceptionMessageWhenFamilyGroupDoesNotExist() {
        FamilyGroupNotFoundException exception = new FamilyGroupNotFoundException(42L);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleFamilyGroupNotFound(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Family group 42 not found");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnConflictWithGenericMessageWhenDatabaseConstraintIsViolated() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("constraint violated");

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleDataIntegrityViolation(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.message()).isEqualTo("Request violates a database constraint");
        assertThat(body.details()).isEmpty();
    }

    @Test
    void shouldReturnInternalServerErrorWithGenericMessageWhenExceptionIsUnexpected() {
        RuntimeException exception = new RuntimeException("boom");

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleUnexpectedError(exception);
        ApiErrorDto body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.message()).isEqualTo("Unexpected error");
        assertThat(body.details()).isEmpty();
    }
}
