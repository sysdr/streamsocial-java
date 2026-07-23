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
 * in a Testcontainers-managed container - no mocked AdminClient. Requires
 * Docker to be running.
 *
 * <p>Production specs use replication factor 3 and large partition counts
 * (the Day 2/3 cluster). This test uses RF=1 and smaller partition counts
 * with unique topic names per method so bootstrapper behavior can be
 * exercised reliably on one broker without delete/recreate races.
 */
@Testcontainers
class TopicBootstrapperIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private Admin newAdminClient() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    private static TopicSpec topic(String name, int partitions) {
        return new TopicSpec(
                name,
                partitions,
                (short) 1,
                Map.of(
                        "cleanup.policy", "delete",
                        "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000),
                        "min.insync.replicas", "1"
                )
        );
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void firstRunCreatesBothTopics() throws Exception {
        List<TopicSpec> catalog = List.of(
                topic(unique("user-actions"), 8),
                topic(unique("content-interactions"), 4)
        );

        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(catalog);

            assertThat(result.created())
                    .containsExactlyInAnyOrder(catalog.get(0).name(), catalog.get(1).name());
            assertThat(result.alreadyPresentVerified()).isEmpty();
            assertThat(result.hasMismatches()).isFalse();
        }
    }

    @Test
    void secondRunCreatesNothingAndVerifiesBoth() throws Exception {
        List<TopicSpec> catalog = List.of(
                topic(unique("user-actions"), 8),
                topic(unique("content-interactions"), 4)
        );

        try (Admin adminClient = newAdminClient()) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);

            bootstrapper.bootstrap(catalog); // first run
            TopicBootstrapper.BootstrapResult second = bootstrapper.bootstrap(catalog);

            assertThat(second.created()).isEmpty();
            assertThat(second.alreadyPresentVerified())
                    .containsExactlyInAnyOrder(catalog.get(0).name(), catalog.get(1).name());
            assertThat(second.hasMismatches()).isFalse();
        }
    }

    @Test
    void reportsMismatchWhenExistingTopicHasWrongPartitionCount() throws Exception {
        String userActions = unique("user-actions");
        String contentInteractions = unique("content-interactions");
        List<TopicSpec> catalog = List.of(
                topic(userActions, 8),
                topic(contentInteractions, 4)
        );

        try (Admin adminClient = newAdminClient()) {
            // Simulate a topic that was created by hand with the wrong
            // partition count before anyone ran the bootstrapper.
            adminClient.createTopics(List.of(new NewTopic(userActions, 4, (short) 1))).all().get();

            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(catalog);

            assertThat(result.created()).containsExactly(contentInteractions);
            assertThat(result.hasMismatches()).isTrue();
            assertThat(result.mismatched().get(0)).contains(userActions, "expected 8", "found 4");
        }
    }
}
