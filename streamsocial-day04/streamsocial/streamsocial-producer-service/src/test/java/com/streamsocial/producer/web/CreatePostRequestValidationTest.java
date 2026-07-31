package com.streamsocial.producer.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePostRequestValidationTest {

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
    void validRequestHasNoViolations() {
        CreatePostRequest request = new CreatePostRequest(UUID.randomUUID(), "hello StreamSocial");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void blankContentIsRejected() {
        CreatePostRequest request = new CreatePostRequest(UUID.randomUUID(), "   ");
        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("content", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void missingUserIdIsRejected() {
        CreatePostRequest request = new CreatePostRequest(null, "hello");
        Set<ConstraintViolation<CreatePostRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("userId", violations.iterator().next().getPropertyPath().toString());
    }
}
