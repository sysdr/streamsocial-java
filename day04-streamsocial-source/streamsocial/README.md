# StreamSocial (Day 4)

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
This snapshot is through **Day 4: High-Volume Producer Implementation**.

## Modules

| Module | Responsibility |
|---|---|
| `streamsocial-common` | Event taxonomy + topic provisioning |
| `streamsocial-infra` | 3-broker KRaft Kafka cluster (Docker Compose) |
| `streamsocial-producer-service` | Spring Boot REST producer → `user-actions` |

No consumer yet — that is Day 5.

## Prerequisites

- Java 21+, Maven 3.9+
- Docker Desktop or Docker Engine + Compose (4GB+ RAM)
- Internet access for first Maven dependency download

## Quick start (from `streamsocial/`)

```bash
./start.sh          # build, test, start Kafka, topics, producer demo
./stop.sh           # stop containers, clean Maven output
./stop.sh --wipe    # also delete Docker volumes
```

## Manual steps

All commands below assume you are in `streamsocial/` unless noted.

### 1. Start the Kafka cluster

```bash
cd streamsocial-infra
./scripts/start.sh
```

Expected: all three `streamsocial-kafka-N` containers `healthy`.
Bootstrap servers: `localhost:29092,localhost:29093,localhost:29094`.

```bash
./scripts/verify-cluster.sh   # optional: quorum check
./scripts/demo-failover.sh    # optional: controller failover demo
```

### 2. Create topics

```bash
cd ..   # back to streamsocial/
mvn -q -pl streamsocial-common -am package -DskipTests
mvn -q -pl streamsocial-common exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

```bash
cd streamsocial-infra && ./scripts/list-topics.sh && cd ..
```

Expected: `user-actions` (1000 partitions), `content-interactions` (500).

### 3. Run the producer

```bash
./run-producer.sh
```

(`./run-producer.sh` frees port 8081 first if an old process is still using it.)

### 4. Publish an event

```bash
curl -X POST http://localhost:8081/api/v1/actions \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
```

Expected: HTTP **202** with `eventId`, `topic`, `partition`, `offset`.

Invalid body (`{}`) → HTTP **400** with `userId` / `actionType` errors.

### Stop the cluster only

```bash
cd streamsocial-infra
./scripts/stop.sh          # keep volumes
./scripts/stop.sh --wipe   # delete volumes too
```

## Extra demos / tests

```bash
# Raw KafkaProducer (no Spring)
mvn -q -pl streamsocial-producer-service -am exec:java \
  -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo

# JMH throughput benchmark
mvn -q -pl streamsocial-producer-service -am compile exec:java \
  -Dexec.mainClass=org.openjdk.jmh.Main

# Full build + tests (needs Docker for Testcontainers)
mvn -q -pl streamsocial-common,streamsocial-producer-service -am verify
```

## Lesson materials

Day 4 guide/article/diagrams: `docs/day04/`.
Earlier days: sibling folders `day01-streamsocial-source` … `day03-streamsocial-source`.

## What's next

Day 5: `@KafkaListener` on `content-interactions`.
