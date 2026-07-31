package com.streamsocial.common.topic;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class TopicBootstrapIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).withKraft();

    @Test
    void createsTopicsOnlyOnce() throws Exception {
        TopicBootstrap bootstrap = new TopicBootstrap(KAFKA.getBootstrapServers());

        List<TopicSpec> specs = List.of(
                new TopicSpec("user-actions", 6, (short) 1),
                new TopicSpec("content-interactions", 3, (short) 1)
        );

        Set<String> firstRun = bootstrap.ensureTopics(specs);
        assertEquals(Set.of("user-actions", "content-interactions"), firstRun);

        Set<String> secondRun = bootstrap.ensureTopics(specs);
        assertTrue(secondRun.isEmpty(), "re-running ensureTopics must not attempt to recreate existing topics");

        assertRealPartitionCount("user-actions", 6);
        assertRealPartitionCount("content-interactions", 3);

        bootstrap.close();
    }

    private void assertRealPartitionCount(String topic, int expectedPartitions) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (Admin admin = Admin.create(props)) {
            int actual = admin.describeTopics(List.of(topic))
                    .allTopicNames().get()
                    .get(topic)
                    .partitions()
                    .size();
            assertEquals(expectedPartitions, actual);
        }
    }
}
