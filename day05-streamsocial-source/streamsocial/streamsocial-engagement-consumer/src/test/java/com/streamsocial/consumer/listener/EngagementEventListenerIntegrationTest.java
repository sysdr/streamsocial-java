package com.streamsocial.consumer.listener;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.common.event.InteractionType;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Spring context, real broker in a container, a real producer
 * publishing onto the topic, and the actual {@link EngagementEventListener}
 * bean doing the consuming - no mocked {@code KafkaConsumer} anywhere.
 */
@Testcontainers
@SpringBootTest
class EngagementEventListenerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsocial.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void createTopic() throws Exception {
        try (Admin admin = Admin.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("content-interactions", 3, (short) 1))).all().get();
        }
    }

    @Autowired
    private EngagementEventListener listener;

    @Autowired
    private RecoveryTracker recoveryTracker;

    private KafkaProducer<String, ContentInteractionEvent> testProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaProducer<>(props);
    }

    @Test
    void publishedInteractionIsProcessedAndLogged() throws Exception {
        ContentInteractionEvent event = new ContentInteractionEvent(
                UUID.randomUUID(), Instant.now(), "user-7", "post-normal", InteractionType.CONTENT_LIKED);

        try (KafkaProducer<String, ContentInteractionEvent> producer = testProducer()) {
            producer.send(new ProducerRecord<>("content-interactions", event.contentId(), event)).get();
        }

        boolean found = waitUntil(10_000, () ->
                listener.getProcessed().stream().anyMatch(e -> e.eventId().equals(event.eventId())));

        assertThat(found).as("expected the listener to have processed the published event").isTrue();
    }

    @Test
    void poisonedEventIsRetriedThenRecoveredNotLostSilently() throws Exception {
        ContentInteractionEvent poison = new ContentInteractionEvent(
                UUID.randomUUID(), Instant.now(), "user-9",
                EngagementEventListener.POISON_CONTENT_ID, InteractionType.CONTENT_LIKED);

        int recoveredBefore = recoveryTracker.getRecoveredCount();

        try (KafkaProducer<String, ContentInteractionEvent> producer = testProducer()) {
            producer.send(new ProducerRecord<>("content-interactions", poison.contentId(), poison)).get();
        }

        // 2 retries at 1s backoff means recovery should land within ~5s;
        // give it real headroom rather than a tight deadline.
        boolean recovered = waitUntil(15_000, () -> recoveryTracker.getRecoveredCount() > recoveredBefore);

        assertThat(recovered).as("expected the error handler to recover the poisoned record").isTrue();
        assertThat(listener.getProcessed())
                .as("the poisoned event should never appear as successfully processed")
                .noneMatch(e -> EngagementEventListener.POISON_CONTENT_ID.equals(e.contentId()));
    }

    private boolean waitUntil(long timeoutMillis, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }
}
