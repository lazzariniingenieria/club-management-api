package com.lazzariniingenieria.clubmanagementapi.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.ApiErrorDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    void shouldReturnBadRequestWithFieldDetailsWhenValidationFails() {
        FieldError dniError = new FieldError("loginRequest", "dni", "must not be blank");
        FieldError clubIdError = new FieldError("loginRequest", "clubId", "must not be null");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(dniError, clubIdError));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

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
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiErrorDto> response = globalExceptionHandler.handleValidation(exception);

        assertThat(response.getBody().details()).isEmpty();
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
