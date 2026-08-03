package com.streamsocial.dashboard.feed;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.common.event.InteractionType;
import com.streamsocial.dashboard.web.EventStreamBroadcaster;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Named {@code *IT} - needs a real broker, runs under Failsafe. Same
 * shape as {@code UserActionsFeedBroadcastIT} (Day 4): proves a real,
 * Testcontainers-consumed record produces the correct real broadcast
 * payload, without trying to inspect live SSE bytes outside a servlet
 * container.
 */
@Testcontainers
class ContentInteractionsFeedBroadcastIT {

    private static final String TOPIC = "content-interactions";

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @BeforeAll
    static void createTopic() throws Exception {
        try (Admin admin = Admin.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
        }
    }

    @Test
    void realConsumedEventProducesTheCorrectBroadcastPayload() throws Exception {
        ContentInteractionEvent published = new ContentInteractionEvent(
                UUID.randomUUID(), Instant.now(), "user-dashboard-it",
                "post-dashboard-it", InteractionType.CONTENT_SHARED);
        publish(published);

        ConsumerRecord<String, ContentInteractionEvent> realRecord = consumeOne();
        assertThat(realRecord.value().eventId()).isEqualTo(published.eventId());

        BlockingQueue<LiveFeedItem> broadcasts = new ArrayBlockingQueue<>(1);
        EventStreamBroadcaster recordingBroadcaster = new EventStreamBroadcaster() {
            @Override
            public void broadcast(String streamName, String eventName, Object payload) {
                if ("content-interactions".equals(streamName) && payload instanceof LiveFeedItem item) {
                    broadcasts.offer(item);
                }
            }
        };

        new ContentInteractionsFeedListener(recordingBroadcaster).onInteraction(realRecord);

        LiveFeedItem item = broadcasts.poll(5, TimeUnit.SECONDS);
        assertThat(item).as("expected the listener to broadcast the real event").isNotNull();
        assertThat(item.primaryKey()).isEqualTo(published.userId());
        assertThat(item.eventType()).isEqualTo(published.interactionType().name());
        assertThat(item.detail()).isEqualTo(published.contentId());
        assertThat(item.offset()).isEqualTo(realRecord.offset());
    }

    private void publish(ContentInteractionEvent event) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        try (KafkaProducer<String, ContentInteractionEvent> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC, event.userId(), event)).get();
        }
    }

    private ConsumerRecord<String, ContentInteractionEvent> consumeOne() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dashboard-it-fetcher");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ContentInteractionEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.streamsocial.common.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        try (Consumer<String, ContentInteractionEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, ContentInteractionEvent> records = consumer.poll(Duration.ofSeconds(2));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }
        throw new IllegalStateException("no record consumed within the wait window");
    }
}
