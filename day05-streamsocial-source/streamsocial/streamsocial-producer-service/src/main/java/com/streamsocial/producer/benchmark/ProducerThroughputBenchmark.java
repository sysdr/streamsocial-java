package com.streamsocial.producer.benchmark;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 4 challenge: measure the "pool of producers" instinct against the
 * pattern this module actually uses (one shared producer). Requires the
 * Day 2 cluster and Day 3's {@code user-actions} topic to be running.
 *
 * <p>Run with:
 * {@code mvn -q -pl streamsocial-producer-service -am compile exec:java
 *   -Dexec.mainClass=org.openjdk.jmh.Main}
 *
 * <p>Two scenarios, same total client-side concurrency (8 JMH worker
 * threads), same broker, same topic:
 * <ul>
 *   <li>{@link #sharedProducer} - all 8 threads send through one
 *       {@code KafkaProducer}, created once for the whole benchmark.</li>
 *   <li>{@link #perThreadProducerPool} - each of the 8 threads gets its
 *       own {@code KafkaProducer}, mirroring the JDBC-pool instinct.</li>
 * </ul>
 * Expect {@code sharedProducer} to win: one producer means the client
 * batches records addressed to the same partition together before
 * sending, so a shared instance produces fewer, larger, more efficient
 * network requests. Eight separate producers each batch in isolation -
 * eight small batches instead of one large one - and each additionally
 * pays for its own {@code buffer.memory} allocation and its own
 * background I/O thread for no throughput benefit.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
public class ProducerThroughputBenchmark {

    private static final String BOOTSTRAP_SERVERS = "localhost:29092,localhost:29093,localhost:29094";
    private static final String TOPIC = "user-actions";

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    /** One producer, created once, shared by every JMH worker thread. */
    @State(Scope.Benchmark)
    public static class SharedProducerState {
        KafkaProducer<String, String> producer;
        AtomicInteger counter = new AtomicInteger();

        @Setup(Level.Trial)
        public void setup() {
            producer = new KafkaProducer<>(producerProps());
        }

        @TearDown(Level.Trial)
        public void teardown() {
            producer.close();
        }
    }

    /** A fresh producer per JMH worker thread - the anti-pattern under test. */
    @State(Scope.Thread)
    public static class PerThreadProducerState {
        KafkaProducer<String, String> producer;
        AtomicInteger counter = new AtomicInteger();

        @Setup(Level.Trial)
        public void setup() {
            producer = new KafkaProducer<>(producerProps());
        }

        @TearDown(Level.Trial)
        public void teardown() {
            producer.close();
        }
    }

    @Benchmark
    @Threads(8)
    public void sharedProducer(SharedProducerState state) {
        String key = "shared-" + state.counter.incrementAndGet();
        state.producer.send(new ProducerRecord<>(TOPIC, key, "benchmark-event"));
    }

    @Benchmark
    @Threads(8)
    public void perThreadProducerPool(PerThreadProducerState state) {
        String key = "pooled-" + state.counter.incrementAndGet();
        state.producer.send(new ProducerRecord<>(TOPIC, key, "benchmark-event"));
    }
}
