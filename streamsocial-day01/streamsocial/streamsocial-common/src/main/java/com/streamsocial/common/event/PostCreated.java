package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record PostCreated(
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotNull UUID userId,
        @NotNull UUID postId,
        @NotBlank @Size(max = 2000) String content
) implements UserActionEvent {
}
