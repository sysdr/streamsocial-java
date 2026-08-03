package com.streamsocial.producer.web;

import java.util.UUID;

public record UserActionResponse(
        UUID eventId,
        String topic,
        int partition,
        long offset
) {
}
