package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

public record RebalanceTriggered(
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotBlank String consumerGroupId,
        @PositiveOrZero int partitionsReassigned
) implements SystemEvent {
}
