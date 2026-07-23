package com.streamsocial.producer.web;

import com.streamsocial.common.event.UserActionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserActionRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        CreateUserActionRequest request =
                new CreateUserActionRequest("user-42", UserActionType.POST_CREATED, "post-1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankUserId() {
        CreateUserActionRequest request =
                new CreateUserActionRequest("", UserActionType.POST_CREATED, "post-1");

        Set<ConstraintViolation<CreateUserActionRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("userId");
    }

    @Test
    void rejectsNullActionType() {
        CreateUserActionRequest request = new CreateUserActionRequest("user-42", null, "post-1");

        Set<ConstraintViolation<CreateUserActionRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("actionType");
    }

    @Test
    void targetIdMayBeNull() {
        CreateUserActionRequest request =
                new CreateUserActionRequest("user-42", UserActionType.PROFILE_UPDATED, null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
