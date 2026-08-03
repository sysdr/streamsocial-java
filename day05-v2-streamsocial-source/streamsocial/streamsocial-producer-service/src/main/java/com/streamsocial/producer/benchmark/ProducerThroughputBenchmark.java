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
 * Day 4 challenge, and the actual evidence behind the correction in
 * {@code KafkaProducerConfig}'s Javadoc: measure one shared producer
 * against a per-thread producer pool (what Appendix C describes,
 * inaccurately, as the right shape for "connection pooling" here) -
 * real numbers, not a claim either way.
 *
 * <p><b>Local-demo-scale note:</b> StreamSocial's real target is 5M
 * posts/second in production, at full cluster scale. This benchmark,
 * run on a single machine against a 3-broker cluster with a handful of
 * threads, cannot and should not try to reach that number - it measures
 * the <em>relative</em> throughput difference between the two
 * patterns, which is what today's question actually is. Treat the
 * ops/sec this prints as a comparison, not a capacity plan.
 *
 * <p>Run with:
 * {@code mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-producer-service
 *   -Dexec.mainClass=org.openjdk.jmh.Main}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
public class ProducerThroughputBenchmark {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092,localhost:9093,localhost:9094";
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

    /** A fresh producer per JMH worker thread - Appendix C's setProducerPerThread shape. */
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
