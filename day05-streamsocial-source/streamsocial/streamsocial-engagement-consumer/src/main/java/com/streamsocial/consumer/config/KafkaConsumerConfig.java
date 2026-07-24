package com.streamsocial.consumer.config;

import com.streamsocial.common.event.ContentInteractionEvent;
import com.streamsocial.consumer.listener.RecoveryTracker;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * The curriculum this course grew out of names {@code SeekToCurrentErrorHandler}
 * as the target for today's error-handling challenge. That class has been
 * deprecated since Spring Kafka 2.8 and is gone entirely as of 3.x - this
 * configuration uses its supported replacement, {@link DefaultErrorHandler},
 * which does the same job: on failure, it seeks the failed record (and
 * everything after it in that poll batch) back so the broker redelivers
 * them, retries against the given {@link FixedBackOff}, and only after
 * retries are exhausted hands the record to a recoverer instead of
 * retrying forever.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${streamsocial.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public Validator eventValidator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Bean
    public ConsumerFactory<String, ContentInteractionEvent> engagementConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "engagement-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // The Day 4 producer for this topic (once one exists) publishes
        // with type-info headers disabled, so this deserializer is told
        // the target type directly instead of trusting a header.
        JsonDeserializer<ContentInteractionEvent> valueDeserializer =
                new JsonDeserializer<>(ContentInteractionEvent.class, false);
        valueDeserializer.addTrustedPackages("com.streamsocial.common.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ContentInteractionEvent> engagementListenerContainerFactory(
            ConsumerFactory<String, ContentInteractionEvent> engagementConsumerFactory,
            RecoveryTracker recoveryTracker) {

        ConcurrentKafkaListenerContainerFactory<String, ContentInteractionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(engagementConsumerFactory);

        // 2 retries, 1 second apart, before giving up on a record.
        FixedBackOff backOff = new FixedBackOff(1000L, 2);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoveryTracker::recordRecovery, backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
