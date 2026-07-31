package com.streamsocial.producer.web;

import com.streamsocial.producer.service.PostProducerService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Real broker via Testcontainers, real HTTP-shaped call into the service layer, real consumer
 * reading the actual bytes back off the topic - never a mocked KafkaTemplate. Portable to any
 * standard Docker host or CI runner; see Appendix C in the master prompt template if a given
 * sandbox's Docker bridge blocks Testcontainers' container detection.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostProducerServiceIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withKraft();

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private PostProducerService postProducerService;

    @Test
    void publishedEventIsReadableFromTheRealTopic() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(userId, "verified via Testcontainers");

        PostProducerService.PublishResult result = postProducerService.publish(request).get();
        assertNotNull(result.metadata());

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "verify-group", "true", KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(java.util.List.of("user-actions"));
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "user-actions", Duration.ofSeconds(10));
            assertEquals(userId.toString(), record.key());
            assertEquals(true, record.value().contains("verified via Testcontainers"));
        }
    }
}
