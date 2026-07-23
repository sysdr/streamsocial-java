package com.streamsocial.producer.config;

import com.streamsocial.common.event.UserActionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * One producer factory, one shared {@link KafkaTemplate} bean - not a pool
 * of producers.
 *
 * <p>This is worth being explicit about, because the instinct coming from
 * JDBC connection pools is to assume more producer instances means more
 * throughput. It's backwards for Kafka: {@code KafkaProducer} is
 * explicitly designed to be thread-safe and shared across every thread
 * that needs to publish. {@link DefaultKafkaProducerFactory}, used without
 * a transaction ID, already caches and reuses a single underlying
 * {@code KafkaProducer} for every {@code KafkaTemplate} that asks it for
 * one - so this configuration class, doing nothing clever, is already the
 * correct pattern. The Day 4 benchmark in this module measures exactly
 * how much worse the tempting alternative - one producer per thread -
 * actually performs.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${streamsocial.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, UserActionEvent> userActionProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Every message on this topic is a UserActionEvent - no need to
        // carry a type header on every single record.
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // acks=1 for today: leader-only acknowledgment. Day 11 covers the
        // full acks=0/1/all trade-off; today's lesson is the client, not
        // the durability dial.
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, UserActionEvent> userActionKafkaTemplate(
            ProducerFactory<String, UserActionEvent> userActionProducerFactory) {
        return new KafkaTemplate<>(userActionProducerFactory);
    }
}
