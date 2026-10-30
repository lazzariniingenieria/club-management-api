package com.lazzariniingenieria.clubmanagementapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptNullSoRequirednessStaysWithNotBlank() {
        assertThat(validate(null)).isEmpty();
    }

    @Test
    void shouldAcceptValueExactlyAtTheByteLimit() {
        assertThat(validate("abñ")).isEmpty();
    }

    @Test
    void shouldRejectAsciiValueOverTheByteLimit() {
        assertThat(validate("abcde")).hasSize(1);
    }

    @Test
    void shouldRejectValueUnderTheCharacterLimitWhenAccentsPushItOverTheByteLimit() {
        assertThat(validate("ñññ")).hasSize(1);
    }

    @Test
    void shouldReportTheConfiguredMessage() {
        Set<ConstraintViolation<FourBytesValue>> violations = validate("abcde");

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("value must be at most 4 bytes");
    }

    private Set<ConstraintViolation<FourBytesValue>> validate(String value) {
        return validator.validate(new FourBytesValue(value));
    }

    private record FourBytesValue(@MaxUtf8Bytes(value = 4, message = "value must be at most 4 bytes") String value) {
    }
}
