package com.streamsocial.dashboard.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Deliberately a raw kafka-clients Consumer with a manual poll loop, not @KafkaListener -
 * Day 5 introduces that abstraction; this shows the mechanism underneath it first. A fresh,
 * random group.id on every start means this consumer always reads from "latest": it's a live
 * traffic monitor, not a system of record, so it should never replay history on restart.
 */
@Component
public class UserActionsThroughputConsumer {

    private final ThroughputTracker throughputTracker;
    private final String bootstrapServers;

    private volatile KafkaConsumer<String, String> consumer;
    private volatile boolean running;

    public UserActionsThroughputConsumer(
            ThroughputTracker throughputTracker,
            @Value("${dashboard.kafka.bootstrap-servers}") String bootstrapServers) {
        this.throughputTracker = throughputTracker;
        this.bootstrapServers = bootstrapServers;
    }

    @PostConstruct
    void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "streamsocial-dashboard-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("user-actions"));
        running = true;

        Thread pollThread = new Thread(this::pollLoop, "dashboard-user-actions-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollLoop() {
        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    throughputTracker.recordEvent();
                }
            }
        } catch (WakeupException e) {
            // expected on shutdown
        } finally {
            consumer.close();
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
    }
}
