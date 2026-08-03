package com.streamsocial.consumer.demo;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.common.event.InteractionType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Nothing in this course publishes to {@code content-interactions} yet -
 * that gap gets filled implicitly by whichever later lesson needs a real
 * likes/comments/shares producer. Until then, this generator exists so
 * Day 5's consumer has real, non-mocked traffic to read.
 *
 * <p>Publishes 14 normal events across all three interaction types, plus
 * one deliberately poisoned event ({@code contentId = "post-BOOM"}) so
 * the error-handling demo has something to trip on.
 *
 * <p>Run with (cluster and topics from Day 2/3 must already exist):
 * {@code mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-engagement-consumer
 *   -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator}
 */
public final class EngagementTestDataGenerator {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "content-interactions";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        List<InteractionType> types = List.of(
                InteractionType.CONTENT_LIKED, InteractionType.CONTENT_COMMENTED, InteractionType.CONTENT_SHARED);

        try (KafkaProducer<String, ContentInteractionEvent> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < 14; i++) {
                String userId = "user-" + (i % 5);
                String contentId = "post-" + (i % 4);
                InteractionType type = types.get(i % types.size());
                ContentInteractionEvent event = new ContentInteractionEvent(
                        UUID.randomUUID(), Instant.now(), userId, contentId, type);
                producer.send(new ProducerRecord<>(TOPIC, event.contentId(), event));
            }

            ContentInteractionEvent poison = new ContentInteractionEvent(
                    UUID.randomUUID(), Instant.now(), "user-0", "post-BOOM", InteractionType.CONTENT_LIKED);
            producer.send(new ProducerRecord<>(TOPIC, poison.contentId(), poison));

            producer.flush();
        }

        System.out.println("Published 14 normal ContentInteractionEvents and 1 poisoned event to " + TOPIC);
        System.out.println("Watch streamsocial-engagement-consumer's logs for STRUCTURED_EVENT and STRUCTURED_ERROR lines,");
        System.out.println("and the dashboard's content-interactions panel at http://localhost:8080");
    }

    private EngagementTestDataGenerator() {
    }
}
