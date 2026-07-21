package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;
import java.util.UUID;

/**
 * A fact about something a user deliberately did on StreamSocial.
 *
 * <p>This is the event Day 3 will route onto the {@code user-actions}
 * topic (1000 partitions, keyed by {@code userId}) and Day 4's producer
 * will publish at up to 5M events/second.
 *
 * @param eventId     unique id for this occurrence
 * @param occurredAt  when the action actually happened
 * @param userId      the acting user; this is the future Kafka partition key
 * @param actionType  which of the five user-action types this is
 * @param targetId    id of the thing acted on - a post id for
 *                    {@code POST_CREATED}/{@code POST_DELETED}, another
 *                    user's id for {@code USER_FOLLOWED}/
 *                    {@code USER_UNFOLLOWED}, null for
 *                    {@code PROFILE_UPDATED}
 */
public record UserActionEvent(
        @NotNull UUID eventId,
        @NotNull @PastOrPresent Instant occurredAt,
        @NotBlank String userId,
        @NotNull UserActionType actionType,
        String targetId
) implements DomainEvent {

    @Override
    public String eventType() {
        return actionType.name();
    }
}
