package com.streamsocial.common.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventDescriberTest {

    @Test
    void describesUserActionEvent() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        PostCreated event = new PostCreated(UUID.randomUUID(), Instant.now(), userId, postId, "first post!");

        String description = DomainEventDescriber.describe(event);

        assertTrue(description.contains(userId.toString()));
        assertTrue(description.contains(postId.toString()));
        assertTrue(description.contains("created"));
    }

    @Test
    void describesContentInteractionEvent() {
        PostLiked event = new PostLiked(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID());

        assertTrue(DomainEventDescriber.describe(event).contains("liked"));
    }

    @Test
    void describesSystemEvent() {
        RebalanceTriggered event = new RebalanceTriggered(
                UUID.randomUUID(), Instant.now(), "feed-service-group", 7);

        String description = DomainEventDescriber.describe(event);

        assertTrue(description.contains("feed-service-group"));
        assertTrue(description.contains("7"));
    }
}
