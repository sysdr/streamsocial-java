# Day 5 Guide: Build, Test, and Demo the Engagement Consumer

*Companion to Day 5: Engagement Consumer Development. Source lives in `streamsocial-engagement-consumer/`; this guide walks through what's there.*

## The idea, in pseudo-code

```
KafkaConsumerConfig:
    consumerFactory = DefaultKafkaConsumerFactory(
        bootstrap.servers, group.id="engagement-consumer",
        keyDeserializer=String, valueDeserializer=Json(ContentInteractionEvent))

    errorHandler = DefaultErrorHandler(
        recoverer = RecoveryTracker::recordRecovery,
        backOff = FixedBackOff(1000ms, retries=2))
    # replaces the removed SeekToCurrentErrorHandler - same seek-and-retry job

@KafkaListener("content-interactions"):
    onInteraction(record):
        event = record.value()
        if not valid(event): throw   # -> retried, then recovered
        if event.contentId == "post-BOOM": throw  # today's demo hook
        log STRUCTURED_EVENT ...
        processed.add(event)
```

One config class wiring deserialization and error handling, one listener method doing real work.

## Step 1 — Bring up the cluster and topics

```bash
cd streamsocial-infra && ./scripts/start.sh && cd ..
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

Skip if already running from a previous day.

## Step 2 — Run the consumer

```bash
mvn -pl streamsocial-engagement-consumer -am spring-boot:run
```

**Expected:** Spring Boot startup log, ending quietly — no web server line, since this module has none. It's now polling `content-interactions` with an empty log until traffic arrives.

## Step 3 — Generate test traffic

In another terminal:

```bash
mvn -q -pl streamsocial-engagement-consumer -am exec:java \
  -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator
```

**Expected in the consumer's terminal:** 14 lines like:
```
STRUCTURED_EVENT event=engagement-processed interactionType=CONTENT_LIKED userId=user-0 contentId=post-0 partition=1 offset=0
```

Then, a couple seconds later (two retries at 1-second backoff):
```
STRUCTURED_ERROR event=engagement-processing-recovered topic=content-interactions partition=... offset=... key=post-BOOM reason=simulated processing failure for post-BOOM
```

If you only see 14 lines and no `STRUCTURED_ERROR`, wait a few more seconds — the retries take ~2 seconds to exhaust before recovery logs.

## Step 4 — Run the tests

```bash
mvn -pl streamsocial-engagement-consumer -am test
```

**Expected:** both tests pass. `poisonedEventIsRetriedThenRecoveredNotLostSilently` takes noticeably longer than the first test — it's waiting out the real retry backoff, not asserting instantly.

## Homework

Add a second `@KafkaListener` method to a new class, `EngagementMetricsListener`, that listens to the same `content-interactions` topic but with a **different** `group.id`. Have it maintain a simple in-memory count per `InteractionType`.

Implementation checklist:
1. New listener class, new `@KafkaListener(topics = "content-interactions", groupId = "engagement-metrics")`.
2. A `ConcurrentHashMap<InteractionType, Long>` (or similar) tallying counts, with a getter for tests.
3. A test that publishes a known mix of interaction types and asserts the final counts match.
4. Run both listeners at once (via `spring-boot:run` or in your test) and confirm **both** process every message — think about why a different `group.id` makes that true when Day 6 hasn't even covered consumer groups in depth yet.

## Solution hints

- Two consumers in two different groups both get their own copy of every record — consumer groups are what make records get *split* across consumers, not consumers in different groups compete for the same records. This is worth internalizing now; Day 6 builds directly on it.
- Your metrics listener doesn't need the `DefaultErrorHandler`/retry setup this lesson's main listener has — it's fine (for homework purposes) to let a bad record just log and move on, since the goal here is understanding group isolation, not re-solving today's error handling.
- If your counts come out lower than expected, check whether you created a **new** `ConsumerFactory`/container factory bean for the new group ID, or accidentally reused `engagementListenerContainerFactory` (which is already bound to `group.id=engagement-consumer` at the factory level in some configurations — check whether `groupId` on `@KafkaListener` actually overrides it in your Spring Kafka version, or wire a second factory to be safe).
