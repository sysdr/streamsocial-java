package com.streamsocial.dashboard.feed;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.dashboard.web.EventStreamBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Day 5's panel: every {@code ContentInteractionEvent} published to
 * {@code content-interactions}, shown live - including whatever Day 5's
 * own test-data generator publishes, so this panel is a second, real
 * confirmation that the events {@code EngagementEventListener} is
 * simultaneously processing (in its own, separate consumer group) are
 * really landing on the topic.
 */
@Component
public class ContentInteractionsFeedListener {

    static final String STREAM_NAME = "content-interactions";

    private final EventStreamBroadcaster broadcaster;

    public ContentInteractionsFeedListener(EventStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(
            topics = "content-interactions",
            groupId = "dashboard-live-feed-content-interactions",
            containerFactory = "contentInteractionsListenerContainerFactory")
    public void onInteraction(ConsumerRecord<String, ContentInteractionEvent> record) {
        ContentInteractionEvent event = record.value();
        LiveFeedItem item = new LiveFeedItem(
                "content-interactions",
                event.interactionType().name(),
                event.userId(),
                event.contentId(),
                record.partition(),
                record.offset(),
                event.occurredAt());

        broadcaster.broadcast(STREAM_NAME, "content-interaction", item);
    }
}
