package com.streamsocial.common.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Day 1 demo: builds one instance of every leaf event type, proves the sealed hierarchy
 * covers all of them, and proves Bean Validation rejects a malformed event before it would
 * ever reach a producer. Run via start.sh.
 */
public final class EventTaxonomyDemo {

    public static void main(String[] args) {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        DomainEvent[] events = {
                new PostCreated(UUID.randomUUID(), Instant.now(), userId, postId, "Shipping Day 1 of Kafka Mastery!"),
                new PostDeleted(UUID.randomUUID(), Instant.now(), userId, postId, "author requested removal"),
                new ProfileUpdated(UUID.randomUUID(), Instant.now(), userId, "displayName", "streamsocial_dev"),
                new PostLiked(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), postId),
                new PostShared(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), postId, UUID.randomUUID()),
                new PostCommented(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), postId, "great first lesson"),
                new ServiceHealthChanged(UUID.randomUUID(), Instant.now(), "streamsocial-producer-service", "UP"),
                new RebalanceTriggered(UUID.randomUUID(), Instant.now(), "feed-service-group", 4)
        };

        System.out.println("== DomainEvent taxonomy: " + events.length + " leaf types across 3 sealed categories ==");
        for (DomainEvent event : events) {
            System.out.println("  [" + event.getClass().getSimpleName() + "] " + DomainEventDescriber.describe(event));
        }

        System.out.println();
        System.out.println("== Bean Validation rejecting a malformed event before it reaches a producer ==");
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            PostCreated invalid = new PostCreated(UUID.randomUUID(), Instant.now(), userId, postId, "");
            Set<ConstraintViolation<PostCreated>> violations = validator.validate(invalid);
            for (ConstraintViolation<PostCreated> violation : violations) {
                System.out.println("  REJECTED: " + violation.getPropertyPath() + " " + violation.getMessage());
            }
        }
    }
}
