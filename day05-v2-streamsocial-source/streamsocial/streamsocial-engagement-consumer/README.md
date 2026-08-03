# streamsocial-engagement-consumer

Day 5: the first consumer. `@KafkaListener` reading `content-interactions`
(created Day 3) for likes, comments, and shares, deserializing with
Spring's `JsonDeserializer<ContentInteractionEvent>`, and recovering
gracefully from processing failures via `DefaultErrorHandler`.

No web server — this is a worker process. It stays alive because the
Kafka listener container's poll-loop threads are non-daemon, the same
way a real horizontally-scaled consumer deployment would run.

## Note on SeekToCurrentErrorHandler

The curriculum this course grew out of names `SeekToCurrentErrorHandler`
as today's target class. That class has been deprecated since Spring
Kafka 2.8 and removed entirely in 3.x. This module uses its supported
replacement, `DefaultErrorHandler` — see the Javadoc on
`KafkaConsumerConfig` and `docs/day05/article.md` for detail.

## Prerequisites

- The Day 2 cluster running with Day 3's topics provisioned

## Run the consumer

```bash
mvn -pl streamsocial-engagement-consumer -am spring-boot:run
```

## Generate test traffic

Nothing in this course publishes to `content-interactions` yet, so this
module ships a small generator:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-engagement-consumer \
  -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator
```

**Expected:** in the consumer's log, 14 `STRUCTURED_EVENT
event=engagement-processed ...` lines, then — after two retries — one
`STRUCTURED_ERROR event=engagement-processing-recovered ...` line for
the poisoned `post-BOOM` event. With the dashboard running (Day 4), the
same 14 events also appear live on its new `content-interactions` panel.

## Tests

```bash
mvn -pl streamsocial-engagement-consumer -am verify
```

`EngagementEventListenerIT` (Failsafe, needs Docker) uses a real
Testcontainers broker, a real producer, and the actual
`EngagementEventListener` Spring bean — no mocked consumer.
`poisonedEventIsRetriedThenRecoveredNotLostSilently` proves the error
handler's retry-then-recover path actually runs, not just that it's
configured.

## What's next

Day 6 scales this consumer's pattern up: consumer groups, partition
assignment strategies, and running many replicas of a feed-generation
worker in parallel.
