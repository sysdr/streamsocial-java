# streamsocial-producer-service

Day 4: the first producer. A Spring Boot REST facade over one shared
`KafkaTemplate<String, UserActionEvent>`, publishing onto `user-actions`
(created Day 3) with the event shape defined Day 1.

## A correction from Appendix C

Appendix C describes `DefaultKafkaProducerFactory.setProducerPerThread(true)`
as "what connection pooling means for a Kafka producer." That's not
accurate for this non-transactional use case — see the Javadoc on
`KafkaProducerConfig` for the full explanation. This module uses one
shared producer, matching Kafka's own client documentation, and
`ProducerThroughputBenchmark` measures the actual difference rather than
asserting one.

## Prerequisites

- The Day 2 cluster running, with Day 3's topics provisioned

## Run the service

```bash
mvn -pl streamsocial-producer-service -am spring-boot:run
```

## Publish an event

```bash
curl -X POST http://localhost:8082/api/v1/actions \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
```

**Expected response** (HTTP 202): a JSON body with the generated
`eventId` and the real `topic`, `partition`, and `offset` the event
landed on.

## See the raw client underneath

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-producer-service \
  -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo
```

## Run the JMH benchmark (today's challenge)

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-producer-service \
  -Dexec.mainClass=org.openjdk.jmh.Main
```

Runs both `sharedProducer` and `perThreadProducerPool` and prints
throughput (ops/sec) for each — the actual evidence behind the Appendix C
correction above. **Local-demo-scale note:** this measures the relative
gap between the two patterns on one machine, not StreamSocial's real 5M
posts/second production target — see `docs/day04/article.md`.

## Tests

```bash
mvn -pl streamsocial-producer-service -am verify
```

`CreateUserActionRequestTest` runs under Surefire, no Docker needed.
`UserActionControllerIT` runs under Failsafe during `verify`, needs
Docker, and spins up a real Spring context + broker + HTTP call +
independent consumer verification.

## What's next

Day 5 is the first consumer: reading `content-interactions` with
`@KafkaListener`. `streamsocial-dashboard`, born alongside this module
today, gets its second live panel then too.
