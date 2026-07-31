package com.streamsocial.producer.service;

import com.streamsocial.common.event.PostCreated;
import com.streamsocial.common.event.UserActionEvent;
import com.streamsocial.producer.web.CreatePostRequest;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class PostProducerService {

    static final String TOPIC = "user-actions";

    private final KafkaTemplate<String, UserActionEvent> kafkaTemplate;

    public PostProducerService(KafkaTemplate<String, UserActionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<PublishResult> publish(CreatePostRequest request) {
        PostCreated event = new PostCreated(
                UUID.randomUUID(), Instant.now(), request.userId(), UUID.randomUUID(), request.content());

        // Keyed by userId: every event for the same user lands on the same partition,
        // which is what keeps one user's timeline in order (Day 14 goes deep on this).
        return kafkaTemplate.send(TOPIC, event.userId().toString(), event)
                .thenApply(result -> new PublishResult(event, result.getRecordMetadata()));
    }

    public record PublishResult(PostCreated event, RecordMetadata metadata) {
    }
}
