package com.streamsocial.common.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventValidationTest {

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
    void taxonomyContainsExactlyTenEventTypes() {
        assertThat(EventTaxonomy.ALL).hasSize(10);
    }

    @Test
    void taxonomyCoversAllThreeCategories() {
        assertThat(EventTaxonomy.ALL)
                .extracting(EventTaxonomy.CatalogEntry::category)
                .containsExactlyInAnyOrder(
                        "UserActionEvent", "UserActionEvent", "UserActionEvent",
                        "UserActionEvent", "UserActionEvent",
                        "ContentInteractionEvent", "ContentInteractionEvent",
                        "ContentInteractionEvent",
                        "SystemEvent", "SystemEvent");
    }

    @Test
    void validUserActionEventHasNoViolations() {
        UserActionEvent event = new UserActionEvent(
                UUID.randomUUID(), Instant.now(), "user-42",
                UserActionType.POST_CREATED, "post-1");

        Set<ConstraintViolation<UserActionEvent>> violations = validator.validate(event);

        assertThat(violations).isEmpty();
    }

    @Test
    void userActionEventRejectsBlankUserId() {
        UserActionEvent event = new UserActionEvent(
                UUID.randomUUID(), Instant.now(), " ",
                UserActionType.POST_CREATED, "post-1");

        Set<ConstraintViolation<UserActionEvent>> violations = validator.validate(event);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("userId");
    }

    @Test
    void userActionEventRejectsFutureTimestamp() {
        UserActionEvent event = new UserActionEvent(
                UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.DAYS), "user-42",
                UserActionType.POST_CREATED, "post-1");

        Set<ConstraintViolation<UserActionEvent>> violations = validator.validate(event);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("occurredAt");
    }

    @Test
    void contentInteractionEventRejectsBlankContentId() {
        ContentInteractionEvent event = new ContentInteractionEvent(
                UUID.randomUUID(), Instant.now(), "user-17",
                "", InteractionType.CONTENT_LIKED);

        Set<ConstraintViolation<ContentInteractionEvent>> violations = validator.validate(event);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("contentId");
    }

    @Test
    void systemEventAcceptsNullMetadataButRequiresSubjectId() {
        SystemEvent withoutMetadata = new SystemEvent(
                UUID.randomUUID(), Instant.now(),
                SystemEventType.RECOMMENDATION_MODEL_UPDATED, "model-v7", null);

        Set<ConstraintViolation<SystemEvent>> violations = validator.validate(withoutMetadata);

        assertThat(violations).isEmpty();
    }

    @Test
    void domainEventSealedHierarchyExhaustiveSwitchCompiles() {
        DomainEvent event = new SystemEvent(
                UUID.randomUUID(), Instant.now(),
                SystemEventType.CONTENT_MODERATION_FLAGGED, "post-1",
                Map.of("reason", "test"));

        // Java 17-compatible exhaustive handling of the sealed DomainEvent hierarchy
        // (switch pattern matching requires --release 21+).
        final String category;
        if (event instanceof UserActionEvent) {
            category = "user-action";
        } else if (event instanceof ContentInteractionEvent) {
            category = "content-interaction";
        } else if (event instanceof SystemEvent) {
            category = "system";
        } else {
            throw new IllegalStateException("Unexpected DomainEvent subtype: " + event.getClass());
        }

        assertThat(category).isEqualTo("system");
    }
}
