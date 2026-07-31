package com.streamsocial.producer.web;

import java.time.Instant;
import java.util.UUID;

public record PostCreatedResponse(UUID eventId, UUID postId, Instant occurredAt, int partition, long offset) {
}
