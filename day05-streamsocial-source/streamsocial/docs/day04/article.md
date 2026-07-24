# Day 4: High-Volume Producer Implementation

*Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition, Module 1: Foundation & Core Concepts (Days 1–10)*

## What we'll build today

- `streamsocial-producer-service`: the first Spring Boot module, and the first code in this course that actually publishes to Kafka
- One REST endpoint, one shared `KafkaTemplate<String, UserActionEvent>`, no pool of producers
- A JMH benchmark that proves — with real numbers, not an assertion — why "one shared producer" beats "a producer per thread"

Three days of foundation converge here: Day 1's event shape, Day 2's cluster, Day 3's topic finally carry a real message.

[DIAGRAM: component-architecture]

## The instinct to resist

If you've built services against a relational database, connection pooling is reflexive: a `DataSource` hands out connections from a bounded pool because a single JDBC connection can't safely serve two threads' queries at once, and opening a fresh connection per request is expensive. It's natural to bring that same instinct to `KafkaProducer` — spin up a pool, hand each thread its own instance, avoid contention.

That instinct is wrong for Kafka, and it's worth understanding exactly why, because it's the actual concept behind today's lesson. `KafkaProducer` is documented as thread-safe specifically so application code doesn't need to build a pool around it. One instance, shared across every thread that needs to publish, is the correct and recommended pattern — not a simplification, not something you graduate away from at scale. `DefaultKafkaProducerFactory`, used the way `KafkaProducerConfig` uses it in this module — without a transactional ID — already embodies this: it caches and hands out the same underlying producer to every caller, whether that's one `KafkaTemplate` bean or a hundred concurrent HTTP requests hitting the controller at once.

## Why a shared producer is actually faster, not just simpler

A producer doesn't send a record the moment `.send()` is called. It appends the record to an in-memory batch keyed by partition, and only flushes a batch to the broker when it's full or `linger.ms` elapses (Day 16 goes deep on tuning that). A shared producer sees every thread's records addressed to the same partition and can pack them into one batch — one network round trip carrying many records.

Split that across eight separate producer instances instead, and each one batches in isolation. Eight threads sending to the same partition now produce up to eight small batches instead of one large one — more network requests for the same total data, plus eight separate `buffer.memory` allocations (32MB default, each) and eight background I/O threads doing work that one thread already handled for free. More producers is strictly worse here, not a trade-off.

[DIAGRAM: flowchart]

## What the benchmark actually measures

Today's challenge — implement and load-test the pooled producer — is a JMH microbenchmark (`ProducerThroughputBenchmark`) comparing two scenarios under identical conditions: 8 concurrent threads, same broker, same topic. `sharedProducer` sends through one `KafkaProducer` created once for the whole benchmark run. `perThreadProducerPool` gives each of the 8 threads its own instance — the JDBC-pool instinct, implemented literally, so it can be measured instead of just argued about.

Run it and read the throughput numbers `sharedProducer` and `perThreadProducerPool` report in ops/sec. The gap is the concrete cost of the wrong mental model — not a benchmark artifact, a direct consequence of how batching works.

## The controller's one real design decision

[DIAGRAM: state-machine]

`UserActionController` blocks on the send's returned future with a short timeout before responding to the HTTP caller. That's worth calling out because it looks like it contradicts Day 1's whole argument for event-driven decoupling. It doesn't: the decoupling StreamSocial gets is between this service and whatever consumes `user-actions` downstream — Day 5's engagement consumer, Day 44's trending calculation, services that don't exist yet and shouldn't need to be up right now. It is not decoupling between this endpoint and the one broker it's directly talking to. A caller getting back a 202 with a real `partition` and `offset` — not values the controller invented, the actual `RecordMetadata` read off the producer's ack — knows the event is durably on the log. Getting that confirmation is worth a couple hundred milliseconds of blocking; faking it with an immediate 202 before the broker has acknowledged anything would be a correctness bug wearing a performance optimization's clothes.

## Success criteria for today

`UserActionControllerIntegrationTest` passes — real Spring context, real broker via Testcontainers, real HTTP call, and an independent `KafkaConsumer` proving the message is actually readable off `user-actions`, not just that the endpoint returned 202. Run the JMH benchmark and be able to state the `sharedProducer` vs. `perThreadProducerPool` throughput gap in your own words, tied to batching — not "shared is faster because sharing is good."

Tomorrow: the other side of this topic — Day 5's consumer, reading `content-interactions` for the first time.
