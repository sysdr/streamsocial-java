package com.streamsocial.producer.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The raw {@code kafka-clients} object {@link com.streamsocial.producer.config.KafkaProducerConfig}
 * hands to {@code KafkaTemplate} - shown directly here because this
 * publication teaches Kafka, not Spring annotations.
 *
 * <p>Eight threads share exactly ONE {@code KafkaProducer} instance
 * below. That's the actual production pattern, not a simplification -
 * see the correction in {@code KafkaProducerConfig}'s Javadoc for why.
 *
 * <p>Run with (Day 2 cluster and Day 3 topics must already exist):
 * {@code mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-producer-service
 *   -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo}
 */
public final class RawProducerDemo {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
    private static final String TOPIC = "user-actions";
    private static final int THREAD_COUNT = 8;
    private static final int EVENTS_PER_THREAD = 500;

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");

        AtomicInteger acknowledged = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long start = System.nanoTime();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < EVENTS_PER_THREAD; i++) {
                        String key = "raw-demo-user-" + threadId + "-" + i;
                        producer.send(new ProducerRecord<>(TOPIC, key, "raw-producer-demo-event"),
                                (metadata, exception) -> {
                                    if (exception == null) {
                                        acknowledged.incrementAndGet();
                                    } else {
                                        failed.incrementAndGet();
                                    }
                                });
                    }
                    latch.countDown();
                });
            }

            latch.await();
            producer.flush();
            executor.shutdown();
        }

        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
        int total = THREAD_COUNT * EVENTS_PER_THREAD;

        System.out.println("Sent " + total + " events from " + THREAD_COUNT
                + " threads sharing ONE KafkaProducer instance.");
        System.out.println("Acknowledged: " + acknowledged.get() + ", failed: " + failed.get());
        System.out.printf("Elapsed: %.2fs (%.0f events/sec on this single-instance demo)%n",
                elapsedSeconds, total / elapsedSeconds);
        System.out.println();
        System.out.println("No thread created its own producer, and no thread-local pool was used.");
        System.out.println("See docs/day04/article.md for why that's correct here, not a shortcut.");
    }

    private RawProducerDemo() {
    }
}
