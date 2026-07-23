package com.streamsocial.producer.web;

import java.util.UUID;

/**
 * Confirms where the event actually landed - not just that the HTTP call
 * succeeded. Partition and offset are real broker metadata read back from
 * the producer's ack, not values the controller invents.
 */
public record UserActionResponse(
        UUID eventId,
        String topic,
        int partition,
        long offset
) {
}
