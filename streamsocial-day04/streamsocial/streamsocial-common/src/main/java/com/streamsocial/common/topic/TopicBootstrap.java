package com.streamsocial.common.topic;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Creates topics with an explicit partition/replication shape instead of relying on
 * auto.create.topics.enable, which hands partition count to whichever client happens
 * to touch the topic first. Safe to run every time the system starts: only topics
 * that don't already exist get created.
 */
public final class TopicBootstrap {

    private final Admin admin;

    public TopicBootstrap(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.admin = Admin.create(props);
    }

    TopicBootstrap(Admin admin) {
        this.admin = admin;
    }

    public Set<String> ensureTopics(List<TopicSpec> specs) throws ExecutionException, InterruptedException {
        Set<String> existing = admin.listTopics().names().get();

        List<NewTopic> toCreate = specs.stream()
                .filter(spec -> !existing.contains(spec.name()))
                .map(spec -> new NewTopic(spec.name(), spec.partitions(), spec.replicationFactor()))
                .collect(Collectors.toList());

        if (!toCreate.isEmpty()) {
            admin.createTopics(toCreate).all().get();
        }

        return toCreate.stream().map(NewTopic::name).collect(Collectors.toSet());
    }

    public void close() {
        admin.close();
    }

    public static void main(String[] args) throws Exception {
        String bootstrapServers = System.getenv().getOrDefault(
                "KAFKA_BOOTSTRAP_SERVERS", "localhost:9092,localhost:9093,localhost:9094");
        int userActionsPartitions = Integer.parseInt(
                System.getenv().getOrDefault("USER_ACTIONS_PARTITIONS", "1000"));
        int contentInteractionsPartitions = Integer.parseInt(
                System.getenv().getOrDefault("CONTENT_INTERACTIONS_PARTITIONS", "500"));
        short replicationFactor = Short.parseShort(
                System.getenv().getOrDefault("TOPIC_REPLICATION_FACTOR", "3"));

        TopicBootstrap bootstrap = new TopicBootstrap(bootstrapServers);
        try {
            Set<String> created = bootstrap.ensureTopics(List.of(
                    new TopicSpec("user-actions", userActionsPartitions, replicationFactor),
                    new TopicSpec("content-interactions", contentInteractionsPartitions, replicationFactor)
            ));
            if (created.isEmpty()) {
                System.out.println("Both topics already existed - nothing to create.");
            } else {
                System.out.println("Created: " + created);
            }
        } finally {
            bootstrap.close();
        }
    }
}
