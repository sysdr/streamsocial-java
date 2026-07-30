package com.streamsocial.common.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventValidationTest {

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
    void validPostCreatedHasNoViolations() {
        PostCreated event = new PostCreated(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), "hello world");

        Set<ConstraintViolation<PostCreated>> violations = validator.validate(event);

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankContentFailsValidation() {
        PostCreated event = new PostCreated(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), "  ");

        Set<ConstraintViolation<PostCreated>> violations = validator.validate(event);

        assertEquals(1, violations.size());
        assertEquals("content", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void oversizedContentFailsValidation() {
        String tooLong = "x".repeat(2001);
        PostCreated event = new PostCreated(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), tooLong);

        Set<ConstraintViolation<PostCreated>> violations = validator.validate(event);

        assertEquals(1, violations.size());
    }

    @Test
    void recordsAreValueEqualNotReferenceEqual() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        PostLiked first = new PostLiked(eventId, now, userId, postId);
        PostLiked second = new PostLiked(eventId, now, userId, postId);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void everyLeafEventImplementsDomainEvent() {
        DomainEvent[] events = {
                new PostCreated(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), "hi"),
                new PostDeleted(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), "spam"),
                new ProfileUpdated(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "bio", "new bio"),
                new PostLiked(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID()),
                new PostShared(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                new PostCommented(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), "nice post"),
                new ServiceHealthChanged(UUID.randomUUID(), Instant.now(), "streamsocial-producer-service", "UP"),
                new RebalanceTriggered(UUID.randomUUID(), Instant.now(), "feed-service-group", 12)
        };

        for (DomainEvent event : events) {
            assertTrue(validator.validate(event).isEmpty(), () -> event.getClass().getSimpleName() + " should be valid");
        }
    }
}
