package com.streamsocial.common.admin;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TopicBootstrapper} against a real, single-node Kafka broker
 * in a Testcontainers-managed container - no mocked AdminClient. Requires
 * Docker to be running.
 *
 * <p>Uses RF=1 copies of the production catalog shapes. {@link StreamSocialTopics}
 * targets the 3-broker Docker Compose cluster (RF=3 / min.ISR=2), which a
 * single Testcontainers broker cannot honor.
 */
@Testcontainers
class TopicBootstrapperIntegrationTest {

    private static final List<TopicSpec> SINGLE_BROKER_TOPICS = List.of(
            new TopicSpec("user-actions", 10, (short) 1, Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                    "min.insync.replicas", "1")),
            new TopicSpec("content-interactions", 5, (short) 1, Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                    "min.insync.replicas", "1")));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private Admin newAdminClient() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    @BeforeEach
    void deleteCatalogTopics() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            adminClient.deleteTopics(List.of("user-actions", "content-interactions"))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Topics may not exist yet on a fresh container.
        }
        // Give the broker a moment to finish delete before the next create.
        Thread.sleep(500);
    }

    @Test
    void firstRunCreatesBothTopics() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(SINGLE_BROKER_TOPICS);

            assertThat(result.created()).containsExactlyInAnyOrder("user-actions", "content-interactions");
            assertThat(result.alreadyPresentVerified()).isEmpty();
            assertThat(result.hasMismatches()).isFalse();
        }
    }

    @Test
    void secondRunCreatesNothingAndVerifiesBoth() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            bootstrapper.bootstrap(SINGLE_BROKER_TOPICS); // first run
            TopicBootstrapper.BootstrapResult second = bootstrapper.bootstrap(SINGLE_BROKER_TOPICS);

            assertThat(second.created()).isEmpty();
            assertThat(second.alreadyPresentVerified())
                    .containsExactlyInAnyOrder("user-actions", "content-interactions");
            assertThat(second.hasMismatches()).isFalse();
        }
    }

    @Test
    void reportsMismatchWhenExistingTopicHasWrongPartitionCount() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            // Simulate a topic that was created by hand with the wrong
            // partition count before anyone ran the bootstrapper.
            adminClient.createTopics(List.of(new NewTopic("user-actions", 4, (short) 1))).all().get();

            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(SINGLE_BROKER_TOPICS);

            assertThat(result.created()).containsExactly("content-interactions");
            assertThat(result.hasMismatches()).isTrue();
            assertThat(result.mismatched().get(0)).contains("user-actions", "expected 10", "found 4");
        }
    }
}
