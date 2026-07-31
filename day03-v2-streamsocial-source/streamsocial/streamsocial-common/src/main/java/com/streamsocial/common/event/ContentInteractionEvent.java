package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact about a user reacting to someone else's content.
 *
 * <p>This is the event Day 5's engagement consumer will read from the
 * {@code content-interactions} topic (500 partitions). Splitting this out
 * from {@link UserActionEvent} matters for partitioning: interaction volume
 * scales with content virality, not with active-user count, so it earns its
 * own topic and its own partition strategy from Day 3 onward.
 *
 * @param eventId          unique id for this occurrence
 * @param occurredAt       when the interaction happened
 * @param userId           the user who reacted
 * @param contentId        the post, comment, or media item reacted to -
 *                         this is the future Kafka partition key, so trend
 *                         detection in Day 44 can aggregate per content item
 * @param interactionType  like, comment, or share
 */
public record ContentInteractionEvent(
        @NotNull UUID eventId,
        @NotNull @PastOrPresent Instant occurredAt,
        @NotBlank String userId,
        @NotBlank String contentId,
        @NotNull InteractionType interactionType
) implements DomainEvent {

    @Override
    public String eventType() {
        return interactionType.name();
    }
}
