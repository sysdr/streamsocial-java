package com.streamsocial.dashboard.feed;

import java.time.Instant;

/**
 * What actually goes out over SSE - a deliberately thin projection of
 * whatever domain event triggered it, not the raw record. Keeps the
 * dashboard's wire format stable even if an event record's shape
 * changes later.
 */
public record LiveFeedItem(
        String topic,
        String eventType,
        String primaryKey,
        String detail,
        int partition,
        long offset,
        Instant occurredAt
) {
}
