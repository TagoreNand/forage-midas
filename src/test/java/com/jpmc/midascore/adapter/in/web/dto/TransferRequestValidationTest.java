package com.jpmc.midascore.adapter.in.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void valid_request_has_no_violations() {
        assertThat(validator.validate(new TransferRequest(1L, 2L, new BigDecimal("10.00")))).isEmpty();
    }

    @Test
    void missing_sender_is_rejected() {
        assertThat(validator.validate(new TransferRequest(null, 2L, new BigDecimal("10.00")))).isNotEmpty();
    }

    @Test
    void non_positive_amount_is_rejected() {
        assertThat(validator.validate(new TransferRequest(1L, 2L, new BigDecimal("0.00")))).isNotEmpty();
    }
}
