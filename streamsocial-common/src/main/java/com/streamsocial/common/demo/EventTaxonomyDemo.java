package com.streamsocial.common.demo;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.common.event.DomainEvent;
import com.streamsocial.common.event.EventTaxonomy;
import com.streamsocial.common.event.InteractionType;
import com.streamsocial.common.event.SystemEvent;
import com.streamsocial.common.event.SystemEventType;
import com.streamsocial.common.event.UserActionEvent;
import com.streamsocial.common.event.UserActionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Day 1 demo. No Kafka broker exists yet - that arrives Day 2 - so this
 * demonstrates the two things that actually matter today: the taxonomy is
 * complete (10 types, three categories) and invalid events are rejected
 * before they ever get near a producer.
 *
 * <p>Run with:
 * {@code mvn -q -pl streamsocial-common exec:java
 *   -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo}
 */
public final class EventTaxonomyDemo {

    public static void main(String[] args) {
        printCatalog();
        System.out.println();
        constructValidEvents();
        System.out.println();
        rejectInvalidEvent();
    }

    private static void printCatalog() {
        System.out.println("StreamSocial event taxonomy - " + EventTaxonomy.ALL.size() + " types");
        System.out.println("-".repeat(60));
        String currentCategory = "";
        for (EventTaxonomy.CatalogEntry entry : EventTaxonomy.ALL) {
            if (!entry.category().equals(currentCategory)) {
                currentCategory = entry.category();
                System.out.println();
                System.out.println(currentCategory + ":");
            }
            System.out.printf("  %-28s %s%n", entry.eventType(), entry.description());
        }
    }

    private static void constructValidEvents() {
        System.out.println("Constructing one event per category:");

        DomainEvent postCreated = new UserActionEvent(
                UUID.randomUUID(), Instant.now(), "user-42",
                UserActionType.POST_CREATED, "post-9001");

        DomainEvent contentLiked = new ContentInteractionEvent(
                UUID.randomUUID(), Instant.now(), "user-17",
                "post-9001", InteractionType.CONTENT_LIKED);

        DomainEvent moderationFlag = new SystemEvent(
                UUID.randomUUID(), Instant.now(),
                SystemEventType.CONTENT_MODERATION_FLAGGED, "post-9001",
                Map.of("reason", "spam_report_threshold_exceeded"));

        for (DomainEvent event : Set.of(postCreated, contentLiked, moderationFlag)) {
            System.out.println("  " + describe(event));
        }
    }

    private static void rejectInvalidEvent() {
        System.out.println("Validating a malformed event (blank userId):");

        UserActionEvent malformed = new UserActionEvent(
                UUID.randomUUID(), Instant.now(), "", // blank userId - should fail @NotBlank
                UserActionType.POST_CREATED, "post-9001");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<UserActionEvent>> violations = validator.validate(malformed);

            if (violations.isEmpty()) {
                System.out.println("  unexpectedly valid - this should not happen");
            } else {
                for (ConstraintViolation<UserActionEvent> violation : violations) {
                    System.out.println("  rejected: " + violation.getPropertyPath()
                            + " " + violation.getMessage());
                }
            }
        }
    }

    private static String describe(DomainEvent event) {
        return event.getClass().getSimpleName() + " [" + event.eventType()
                + "] eventId=" + event.eventId();
    }

    private EventTaxonomyDemo() {
    }
}
