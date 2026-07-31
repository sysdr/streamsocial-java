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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link TopicBootstrapper} against a real, single-node Kafka broker
 * in a Testcontainers-managed container - no mocked AdminClient. Named
 * {@code *IT} (not {@code *Test}) deliberately: Surefire's default
 * include pattern never matches this suffix, so this class only runs
 * under Failsafe (bound in this module's own {@code pom.xml}), during
 * {@code mvn verify}, not plain {@code mvn test}. Requires Docker.
 *
 * <p>Uses RF=1 specs (not {@link StreamSocialTopics#ALL}) because the
 * container is a single broker; production RF=3 / min.ISR=2 only apply
 * to the Day 2 three-broker cluster. Each test uses a unique topic-name
 * prefix so tests stay isolated without racing Kafka's async deletes.
 */
@Testcontainers
class TopicBootstrapperIT {

    private static final int USER_ACTIONS_PARTITIONS = 12;
    private static final int CONTENT_INTERACTIONS_PARTITIONS = 6;

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private Admin newAdminClient() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    private static List<TopicSpec> catalog(String prefix) {
        return List.of(
                new TopicSpec(prefix + "user-actions", USER_ACTIONS_PARTITIONS, (short) 1,
                        Map.of("cleanup.policy", "delete", "min.insync.replicas", "1")),
                new TopicSpec(prefix + "content-interactions", CONTENT_INTERACTIONS_PARTITIONS, (short) 1,
                        Map.of("cleanup.policy", "delete", "min.insync.replicas", "1"))
        );
    }

    private static String uniquePrefix() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    }

    @Test
    void firstRunCreatesBothTopics() throws Exception {
        String prefix = uniquePrefix();
        List<TopicSpec> topics = catalog(prefix);

        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(topics);

            assertThat(result.created()).containsExactlyInAnyOrder(
                    prefix + "user-actions", prefix + "content-interactions");
            assertThat(result.alreadyPresentVerified()).isEmpty();
            assertThat(result.hasMismatches()).isFalse();
        }
    }

    @Test
    void secondRunCreatesNothingAndVerifiesBoth() throws Exception {
        String prefix = uniquePrefix();
        List<TopicSpec> topics = catalog(prefix);

        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            bootstrapper.bootstrap(topics); // first run
            TopicBootstrapper.BootstrapResult second = bootstrapper.bootstrap(topics);

            assertThat(second.created()).isEmpty();
            assertThat(second.alreadyPresentVerified())
                    .containsExactlyInAnyOrder(prefix + "user-actions", prefix + "content-interactions");
            assertThat(second.hasMismatches()).isFalse();
        }
    }

    @Test
    void reportsMismatchWhenExistingTopicHasWrongPartitionCount() throws Exception {
        String prefix = uniquePrefix();
        List<TopicSpec> topics = catalog(prefix);
        String userActions = prefix + "user-actions";

        try (Admin adminClient = newAdminClient()) {
            // Simulate a topic that was created by hand with the wrong
            // partition count before anyone ran the bootstrapper.
            adminClient.createTopics(List.of(new NewTopic(userActions, 4, (short) 1))).all().get();

            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(topics);

            assertThat(result.created()).containsExactly(prefix + "content-interactions");
            assertThat(result.hasMismatches()).isTrue();
            assertThat(result.mismatched().get(0))
                    .contains(userActions, "found 4")
                    .contains("expected " + USER_ACTIONS_PARTITIONS);
        }
    }
}
