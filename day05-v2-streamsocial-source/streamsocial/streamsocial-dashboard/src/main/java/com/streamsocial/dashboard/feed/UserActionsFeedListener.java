package com.streamsocial.dashboard.feed;

import com.streamsocial.common.event.UserActionEvent;
import com.streamsocial.dashboard.web.EventStreamBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Day 4's panel: every {@code UserActionEvent} the Day 4 producer
 * publishes, shown live. This is real backing data - whatever a reader
 * posts through {@code streamsocial-producer-service}'s REST endpoint
 * appears here within moments, not a synthetic timeseries.
 */
@Component
public class UserActionsFeedListener {

    static final String STREAM_NAME = "user-actions";

    private final EventStreamBroadcaster broadcaster;

    public UserActionsFeedListener(EventStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(
            topics = "user-actions",
            groupId = "dashboard-live-feed",
            containerFactory = "userActionsListenerContainerFactory")
    public void onUserAction(ConsumerRecord<String, UserActionEvent> record) {
        UserActionEvent event = record.value();
        LiveFeedItem item = new LiveFeedItem(
                "user-actions",
                event.actionType().name(),
                event.userId(),
                event.targetId() != null ? event.targetId() : "-",
                record.partition(),
                record.offset(),
                event.occurredAt());

        broadcaster.broadcast(STREAM_NAME, "user-action", item);
    }
}
