package com.streamsocial.producer.web;

import com.streamsocial.common.event.UserActionType;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Spring context, real broker in a container, a real HTTP call, and
 * a real independent consumer reading the message back off the topic -
 * no mocked {@code KafkaTemplate} anywhere in this test.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserActionControllerIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsocial.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void createTopic() throws Exception {
        // Test cluster only needs the topic to exist, not 1000 partitions -
        // Day 3's partition count is a production sizing decision, not a
        // correctness requirement these tests need to reproduce.
        try (Admin admin = Admin.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("user-actions", 3, (short) 1))).all().get();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/actions";
    }

    @Test
    void postingActionReturnsRealPartitionAndOffset() {
        CreateUserActionRequest request =
                new CreateUserActionRequest("user-42", UserActionType.POST_CREATED, "post-1");

        ResponseEntity<UserActionResponse> response =
                restTemplate.postForEntity(baseUrl(), request, UserActionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UserActionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.topic()).isEqualTo("user-actions");
        assertThat(body.partition()).isGreaterThanOrEqualTo(0);
        assertThat(body.offset()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void invalidRequestReturns400WithFieldErrors() {
        CreateUserActionRequest request = new CreateUserActionRequest("", null, null);

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl(), request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKeys("userId", "actionType");
    }

    @Test
    void publishedEventIsActuallyReadableFromTheTopic() {
        CreateUserActionRequest request =
                new CreateUserActionRequest("user-99", UserActionType.USER_FOLLOWED, "user-100");

        restTemplate.postForEntity(baseUrl(), request, UserActionResponse.class);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-verifier");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("user-actions"));

            boolean found = false;
            long deadline = System.currentTimeMillis() + 10_000;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    if ("user-99".equals(record.key()) && record.value().contains("USER_FOLLOWED")) {
                        found = true;
                        break;
                    }
                }
            }

            assertThat(found)
                    .as("expected to read back a USER_FOLLOWED event keyed by user-99")
                    .isTrue();
        }
    }
}
