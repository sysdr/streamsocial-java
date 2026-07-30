package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A fact StreamSocial's own backend generated, not a direct user action.
 *
 * <p>These events matter for a different reason than the other two types:
 * they are what Day 57's notification and recommendation microservices
 * will subscribe to once the monolith-ish services split apart. A
 * moderation flag or a model refresh has to reach multiple downstream
 * services without any of them polling a database - that's the whole
 * argument for event-driven architecture this lesson opens with.
 *
 * @param eventId          unique id for this occurrence
 * @param occurredAt       when StreamSocial's backend generated this event
 * @param systemEventType  moderation flag or model update
 * @param subjectId        id of the thing this event is about - a content
 *                         id for {@code CONTENT_MODERATION_FLAGGED}, a
 *                         model version string for
 *                         {@code RECOMMENDATION_MODEL_UPDATED}
 * @param metadata         small, flat key-value context (e.g. moderation
 *                         reason code); kept generic here deliberately,
 *                         Day 26 replaces this with a typed JSON Schema
 */
public record SystemEvent(
        @NotNull UUID eventId,
        @NotNull @PastOrPresent Instant occurredAt,
        @NotNull SystemEventType systemEventType,
        @NotBlank String subjectId,
        Map<String, String> metadata
) implements DomainEvent {

    @Override
    public String eventType() {
        return systemEventType.name();
    }
}
