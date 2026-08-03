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
 * in a Testcontainers-managed container - no mocked AdminClient. Named
 * {@code *IT} (not {@code *Test}) deliberately: Surefire's default
 * include pattern never matches this suffix, so this class only runs
 * under Failsafe (bound in this module's own {@code pom.xml}), during
 * {@code mvn verify}, not plain {@code mvn test}. Requires Docker.
 *
 * <p>Uses RF=1 topic specs (not {@link StreamSocialTopics#ALL}) because a
 * single-broker Testcontainers Kafka cannot honor production RF=3.
 */
@Testcontainers
class TopicBootstrapperIT {

    private static final TopicSpec USER_ACTIONS = new TopicSpec(
            "user-actions",
            12,
            (short) 1,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                    "min.insync.replicas", "1"
            )
    );

    private static final TopicSpec CONTENT_INTERACTIONS = new TopicSpec(
            "content-interactions",
            6,
            (short) 1,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                    "min.insync.replicas", "1"
            )
    );

    private static final List<TopicSpec> CATALOG = List.of(USER_ACTIONS, CONTENT_INTERACTIONS);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private Admin newAdminClient() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    @Test
    void firstRunCreatesBothTopics() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(CATALOG);

            assertThat(result.created()).containsExactlyInAnyOrder("user-actions", "content-interactions");
            assertThat(result.alreadyPresentVerified()).isEmpty();
            assertThat(result.hasMismatches()).isFalse();
        }
    }

    @Test
    void secondRunCreatesNothingAndVerifiesBoth() throws Exception {
        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            bootstrapper.bootstrap(CATALOG); // first run
            TopicBootstrapper.BootstrapResult second = bootstrapper.bootstrap(CATALOG);

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
            // Unique name avoids cross-test interference on the shared container.
            TopicSpec mismatchedUserActions = new TopicSpec(
                    "user-actions-mismatch",
                    USER_ACTIONS.partitions(),
                    (short) 1,
                    USER_ACTIONS.configs()
            );
            adminClient.createTopics(List.of(
                    new NewTopic(mismatchedUserActions.name(), 4, (short) 1)
            )).all().get();

            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(
                    List.of(mismatchedUserActions, CONTENT_INTERACTIONS));

            assertThat(result.created()).containsExactly("content-interactions");
            assertThat(result.hasMismatches()).isTrue();
            assertThat(result.mismatched().get(0))
                    .contains("user-actions-mismatch", "found 4")
                    .contains("expected " + USER_ACTIONS.partitions());
        }
    }
}
