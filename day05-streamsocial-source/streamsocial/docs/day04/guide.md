# Day 4 Guide: Build, Test, and Benchmark the Producer

*Companion to Day 4: High-Volume Producer Implementation. Source lives in `streamsocial-producer-service/`; this guide walks through what's there.*

## The idea, in pseudo-code

```
KafkaProducerConfig:
    producerFactory = DefaultKafkaProducerFactory(bootstrap.servers, ...)
    # no transactional id -> factory caches ONE shared producer instance
    kafkaTemplate = KafkaTemplate(producerFactory)

POST /api/v1/actions(request):
    event = UserActionEvent(newId(), now(), request.userId, request.actionType, request.targetId)
    result = kafkaTemplate.send("user-actions", key=event.userId, value=event)
              .get(timeout=2s)   # wait for the real broker ack
    return 202, {eventId, topic, partition, offset}  # from the ack, not invented
```

One config class, one endpoint. The interesting part isn't the code — it's why a pool of producers would make this slower, which the benchmark measures directly.

## Step 1 — Bring up the cluster and topics

```bash
cd streamsocial-infra && ./scripts/start.sh && cd ..
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

Skip this if you already have Day 2/3 running — `start.sh` is safe to re-run.

## Step 2 — Run the service

```bash
mvn -pl streamsocial-producer-service -am spring-boot:run
```

**Expected:** Spring Boot startup log ending with the embedded Tomcat listening on port 8081.

## Step 3 — Publish an event

In another terminal:

```bash
curl -X POST http://localhost:8081/api/v1/actions \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
```

**Expected output:**
```json
{"eventId":"...","topic":"user-actions","partition":<0-999>,"offset":<n>}
```

Try it with a blank body (`-d '{}'`) and confirm you get HTTP 400 with `userId`/`actionType` field errors instead of a 500.

## Step 4 — See the raw client

Stop the Spring app (Ctrl+C) or run this in a third terminal:

```bash
mvn -q -pl streamsocial-producer-service -am exec:java \
  -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo
```

**Expected output:** a line confirming 4000 events sent from 8 threads sharing one `KafkaProducer`, an acknowledged/failed count, and a throughput figure.

## Step 5 — Run the benchmark (today's challenge)

```bash
mvn -q -pl streamsocial-producer-service -am compile exec:java \
  -Dexec.mainClass=org.openjdk.jmh.Main
```

This takes a few minutes (2 warmup + 3 measurement iterations × 5 seconds × 2 benchmarks). **Expected output:** a JMH results table with `sharedProducer` and `perThreadProducerPool` throughput in ops/sec. To run just one benchmark, pass a regex filter as an argument, e.g. `-Dexec.args=sharedProducer`.

## Step 6 — Run the tests

```bash
mvn -pl streamsocial-producer-service -am test
```

**Expected:** all green, including `UserActionControllerIntegrationTest`'s three tests (Docker required).

## Homework

Add a second endpoint, `GET /api/v1/actions/health`, that reports whether the shared `KafkaTemplate`'s producer can currently reach the cluster — without sending an actual event to do it.

Implementation checklist:
1. Look at `ProducerFactory`/`KafkaTemplate`'s API for a way to inspect cluster metadata without producing a record (hint: the underlying `KafkaProducer` has a `partitionsFor(topic)` method).
2. Return `200 {"status":"UP", "partitions": <count>}` if metadata resolves, `503` if it throws or times out.
3. Add a test that stops the Testcontainers broker mid-test and confirms the endpoint reports down (or skip this part if your test setup makes stopping the container mid-test impractical — note why in a comment instead).

## Solution hints

- `kafkaTemplate.getProducerFactory().createProducer().partitionsFor("user-actions")` gets you the partition list without publishing anything — remember to close what `createProducer()` hands back if it's not the shared instance in your Spring Kafka version (check whether `closeProducerFor` behavior applies before you call `close()`, since closing the *shared* producer would break every other request).
- Wrap the metadata call with a short timeout using `producer.partitionsFor(topic, Duration)` if available in your Kafka client version, or run it inside a bounded `CompletableFuture` if not.
- This is deliberately a smaller, more contained task than the main lesson — it's meant to make you read `KafkaTemplate`'s actual API surface, not just its `send()` method.
