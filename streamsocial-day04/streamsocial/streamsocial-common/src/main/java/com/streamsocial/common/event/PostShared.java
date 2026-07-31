package com.streamsocial.common.event;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PostShared(
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotNull UUID userId,
        @NotNull UUID postId,
        @NotNull UUID sharedToUserId
) implements ContentInteractionEvent {
}
