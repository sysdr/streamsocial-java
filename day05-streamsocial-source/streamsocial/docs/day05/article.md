# Day 5: Engagement Consumer Development

*Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition, Module 1: Foundation & Core Concepts (Days 1–10)*

## What we'll build today

- `streamsocial-engagement-consumer`: the first consumer, reading `content-interactions` (Day 3) for likes, comments, shares
- `@KafkaListener` with Spring's `JsonDeserializer<ContentInteractionEvent>`, symmetric with Day 4's producer-side serialization choices
- Graceful failure handling — retry, then recover without silently dropping the record

[DIAGRAM: component-architecture]

## A correction worth making out loud

The error-handling class this lesson was originally scoped around, `SeekToCurrentErrorHandler`, doesn't exist in current Spring Kafka. It was deprecated in version 2.8 and removed outright in 3.x. That's not a footnote — it's the kind of thing that happens constantly in a fast-moving ecosystem, and pretending otherwise would mean shipping a lesson that doesn't compile against anything you'd actually install today. The replacement, `DefaultErrorHandler`, does the same job the deprecated class did: on a listener failure, it seeks the failed record — and everything after it in that poll batch — back to be redelivered by the broker, retries against a configurable backoff, and only after retries are exhausted hands the record to a recoverer instead of retrying forever or crashing the consumer thread.

## The poll loop, and what "consuming" actually means

A Kafka consumer doesn't get pushed messages — it polls. `poll()` asks the broker for the next batch of records sitting after the consumer's current offset on whatever partitions it's assigned, gets a batch back, and the application processes that batch before calling `poll()` again. `@KafkaListener` hides this loop entirely: Spring's listener container runs it on a background thread and calls your annotated method once per record (or once per batch, if you ask for that). What you're writing is the body of that loop, not the loop itself — worth knowing, because Day 8's commit strategies and Day 9's rebalance listener both make more sense once "there's a loop underneath this annotation" is a mental model you actually have.

[DIAGRAM: flowchart]

## Deserialization is where the producer's choices come back

Day 4's producer configured `JsonSerializer.ADD_TYPE_INFO_HEADERS = false` — no type metadata riding along on every record, because every message on `content-interactions` is a `ContentInteractionEvent` and saying so on every single message is waste. That decision has a mirror-image requirement on this side: `JsonDeserializer` needs to be told the target type directly, `new JsonDeserializer<>(ContentInteractionEvent.class, false)`, rather than expecting a header that was never sent. Producer and consumer configuration aren't independent — a serialization decision made in one module is a contract the other module has to honor, and getting that pairing wrong is a silent, confusing failure mode (deserialization exceptions with no obvious cause) rather than a loud one.

## Validation didn't stop mattering just because the JSON parsed

Successfully deserializing a record proves the bytes were well-formed JSON matching the expected shape. It proves nothing about whether the *values* make sense. This listener runs every deserialized `ContentInteractionEvent` through the same `jakarta.validation.Validator` Day 1 used — a blank `contentId` that somehow made it onto the topic is caught here, thrown as an exception, and handled by the same retry-then-recover path as any other processing failure. Bean Validation isn't a REST-layer-only concern; it's useful anywhere a payload crosses a trust boundary, and a Kafka topic populated by services you don't fully control is exactly that.

[DIAGRAM: state-machine]

## What "graceful" actually means here

Today's failure hook — a deliberately poisoned `contentId` of `post-BOOM` — exists to make the failure path observable, not because that's a real production scenario. What matters is what happens when it fires: the record gets retried twice, one second apart, and if it still fails, `RecoveryTracker` logs it as `STRUCTURED_ERROR` and counts it — instead of the consumer thread dying (which would stop every other partition assigned to it from being processed too) or the record being silently dropped (which would mean a like nobody ever finds out happened). "Graceful" here specifically means: bounded retries, then a loud, greppable record of what got given up on. It does not yet mean routing the record somewhere it can be reprocessed later — that's a dead letter topic, and that's Day 25's lesson, once poison-pill handling gets the dedicated treatment it deserves instead of being rushed into today's scope.

## Success criteria for today

`EngagementEventListenerIntegrationTest` passes — both the normal-processing case and the poisoned-record recovery case, against a real broker, with the real listener bean. Run the test-data generator against a live consumer and find both `STRUCTURED_EVENT` and `STRUCTURED_ERROR` lines in its log without having to guess whether the error handler actually did anything.

Tomorrow: scaling this pattern — consumer groups, partition assignment, and running many copies of a worker like this one at once.
