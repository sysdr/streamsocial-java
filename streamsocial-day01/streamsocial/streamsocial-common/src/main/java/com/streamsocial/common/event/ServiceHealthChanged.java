package com.streamsocial.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ServiceHealthChanged(
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotBlank String serviceName,
        @NotBlank String status
) implements SystemEvent {
}
