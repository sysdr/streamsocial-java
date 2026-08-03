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
 * One producer factory, one shared {@link KafkaTemplate} bean - not a
 * pool of producers, and not {@code setProducerPerThread(true)} either.
 *
 * <p><b>A correction, stated plainly:</b> this course's reference
 * material (Appendix C) describes {@code DefaultKafkaProducerFactory
 * .setProducerPerThread(true)} - a producer-per-thread pool via
 * {@code ThreadLocal} - as "what connection pooling means for a Kafka
 * producer." That's not accurate for what this controller does.
 * {@code KafkaProducer} is documented by Kafka itself as thread-safe,
 * specifically so a single shared instance can serve every calling
 * thread - the client's own Javadoc states sharing one instance is
 * generally <em>faster</em> than using several, because one producer
 * can batch records addressed to the same partition together before
 * sending; split across several producers, that batching opportunity is
 * lost. {@code setProducerPerThread} exists in Spring Kafka mainly for
 * <em>transactional</em> producers, which can only run one transaction
 * at a time and therefore genuinely need per-thread isolation - Day 18's
 * territory, not today's. {@link DefaultKafkaProducerFactory}, used
 * without a transactional ID exactly as it's used here, already caches
 * and hands out one shared underlying producer to every caller - this
 * configuration, doing nothing clever, is already the correct pattern.
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
