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
 * Day 4 concept: a single KafkaProducer is thread-safe on its own, but under enough concurrent
 * load its internal network client becomes a contention point. setProducerPerThread(true) gives
 * each calling thread its own pooled producer instance (a ThreadLocal pool) instead of every
 * request queuing behind one shared producer - this is what "connection pooling" means for a
 * Kafka producer, as opposed to the JDBC sense of the term.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, UserActionEvent> userActionProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        DefaultKafkaProducerFactory<String, UserActionEvent> factory =
                new DefaultKafkaProducerFactory<>(configProps);
        factory.setProducerPerThread(true);
        return factory;
    }

    @Bean
    public KafkaTemplate<String, UserActionEvent> userActionKafkaTemplate(
            ProducerFactory<String, UserActionEvent> userActionProducerFactory) {
        return new KafkaTemplate<>(userActionProducerFactory);
    }
}
