package com.streamsocial.consumer.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where a failed record ends up after {@link com.streamsocial.consumer.config.KafkaConsumerConfig}'s
 * retries are exhausted. Today this only logs, structured, and counts -
 * routing the record to a dead letter topic instead is a later lesson's
 * job, once poison-pill handling gets its own dedicated treatment.
 */
@Component
public class RecoveryTracker {

    private static final Logger log = LoggerFactory.getLogger(RecoveryTracker.class);

    private final AtomicInteger recoveredCount = new AtomicInteger();

    public void recordRecovery(ConsumerRecord<?, ?> record, Exception exception) {
        recoveredCount.incrementAndGet();
        log.error("STRUCTURED_ERROR event=engagement-processing-recovered topic={} partition={} offset={} key={} reason={}",
                record.topic(), record.partition(), record.offset(), record.key(), exception.getMessage());
    }

    public int getRecoveredCount() {
        return recoveredCount.get();
    }
}
