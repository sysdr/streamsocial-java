package com.streamsocial.producer.web;

import com.streamsocial.common.event.UserActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * What a client sends to create a {@link com.streamsocial.common.event.UserActionEvent}.
 * Deliberately thinner than the event itself - {@code eventId} and
 * {@code occurredAt} are the server's responsibility, not the caller's.
 */
public record CreateUserActionRequest(
        @NotBlank String userId,
        @NotNull UserActionType actionType,
        String targetId
) {
}
