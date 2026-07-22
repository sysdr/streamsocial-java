package com.streamsocial.common.admin;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TopicBootstrapper} against a real, single-node Kafka broker
 * in a Testcontainers-managed container - no mocked AdminClient. Requires
 * Docker to be running.
 *
 * <p>Uses RF=1 topic specs rather than {@link StreamSocialTopics#ALL}: the
 * production catalog asks for replication factor 3, which a single-broker
 * Testcontainers instance cannot satisfy.
 */
@Testcontainers
class TopicBootstrapperIntegrationTest {

    private static final List<TopicSpec> TEST_TOPICS = List.of(
            new TopicSpec(
                    "user-actions",
                    10,
                    (short) 1,
                    Map.of(
                            "cleanup.policy", "delete",
                            "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                            "min.insync.replicas", "1"
                    )
            ),
            new TopicSpec(
                    "content-interactions",
                    5,
                    (short) 1,
                    Map.of(
                            "cleanup.policy", "delete",
                            "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                            "min.insync.replicas", "1"
                    )
            )
    );

    // Per-test container so create/idempotent/mismatch cases do not share topic state.
    @Container
    KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private Admin newAdminClient() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    @Test
    void firstRunCreatesBothTopics() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(TEST_TOPICS);

            assertThat(result.created()).containsExactlyInAnyOrder("user-actions", "content-interactions");
            assertThat(result.alreadyPresentVerified()).isEmpty();
            assertThat(result.hasMismatches()).isFalse();
        }
    }

    @Test
    void secondRunCreatesNothingAndVerifiesBoth() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            bootstrapper.bootstrap(TEST_TOPICS); // first run
            TopicBootstrapper.BootstrapResult second = bootstrapper.bootstrap(TEST_TOPICS);

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
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(TEST_TOPICS);

            assertThat(result.created()).containsExactly("content-interactions");
            assertThat(result.hasMismatches()).isTrue();
            assertThat(result.mismatched().get(0)).contains("user-actions", "expected 10", "found 4");
        }
    }
}
