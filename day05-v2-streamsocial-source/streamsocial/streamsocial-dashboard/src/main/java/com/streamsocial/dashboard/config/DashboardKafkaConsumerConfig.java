package com.streamsocial.dashboard.config;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.common.event.UserActionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * The dashboard's Kafka consumption is deliberately observational, not
 * transactional to the business: every consumer group here means the
 * dashboard never competes with {@code feed-service},
 * {@code engagement-consumer}, or any other real consumer group for
 * partitions, and it commits nothing meaningful to lose - if the
 * dashboard restarts, it just starts watching again from
 * {@code latest}, rather than replaying history like a business
 * consumer would need to.
 *
 * <p>Day 5 adds a second factory, for {@code content-interactions},
 * with its own distinct group id rather than reusing
 * {@code dashboard-live-feed} - two Spring-managed containers sharing
 * one group id but subscribing to different topics is technically
 * supported by Kafka's group protocol, but ties their rebalances
 * together unnecessarily for no benefit here. Separate group ids keep
 * the two feeds fully independent.
 */
@Configuration
public class DashboardKafkaConsumerConfig {

    @Value("${streamsocial.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, UserActionEvent> userActionsConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dashboard-live-feed-user-actions");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        JsonDeserializer<UserActionEvent> valueDeserializer = new JsonDeserializer<>(UserActionEvent.class, false);
        valueDeserializer.addTrustedPackages("com.streamsocial.common.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserActionEvent> userActionsListenerContainerFactory(
            ConsumerFactory<String, UserActionEvent> userActionsConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, UserActionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userActionsConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, ContentInteractionEvent> contentInteractionsConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dashboard-live-feed-content-interactions");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        JsonDeserializer<ContentInteractionEvent> valueDeserializer =
                new JsonDeserializer<>(ContentInteractionEvent.class, false);
        valueDeserializer.addTrustedPackages("com.streamsocial.common.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ContentInteractionEvent> contentInteractionsListenerContainerFactory(
            ConsumerFactory<String, ContentInteractionEvent> contentInteractionsConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ContentInteractionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(contentInteractionsConsumerFactory);
        return factory;
    }
}
