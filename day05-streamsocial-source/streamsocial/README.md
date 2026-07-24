# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
This snapshot is the repository state through **Day 5: Engagement Consumer
Development**.

## Modules

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1, extended Day 3 | Event taxonomy (`DomainEvent`) + idempotent topic provisioning (`TopicBootstrapper`) |
| `streamsocial-infra` | Day 2, extended Day 3 | 3-broker KRaft Kafka cluster (Docker Compose) + verification scripts |
| `streamsocial-producer-service` | Day 4 | Spring Boot REST facade publishing `UserActionEvent` via `KafkaTemplate` |
| `streamsocial-engagement-consumer` | Day 5 | `@KafkaListener` on `content-interactions`, with `DefaultErrorHandler` retry/recovery |

## Requirements

See [`requirements.txt`](requirements.txt):

- Java 17+
- Maven 3.8+
- Docker Engine + Compose plugin, 4GB+ RAM for Docker

## Quick start

```bash
./start.sh      # build, test, start Kafka, provision topics, run demos
./stop.sh       # stop containers, clean Maven output
./stop.sh --wipe
./cleanup.sh    # stop apps + containers, wipe volumes, prune unused Docker
```

## Manual run (recommended for Day 5 demos)

### 1. Start Kafka

```bash
cd streamsocial-infra && ./scripts/start.sh && cd ..
```

Bootstrap servers: `localhost:29092,localhost:29093,localhost:29094`

Useful infra scripts:

```bash
./scripts/verify-cluster.sh   # from streamsocial-infra
./scripts/demo-failover.sh
./scripts/list-topics.sh
./scripts/stop.sh [--wipe]
```

### 2. Build & install

```bash
mvn -pl streamsocial-common,streamsocial-producer-service,streamsocial-engagement-consumer -am install -DskipTests
```

### 3. Provision topics

```bash
mvn -q -f streamsocial-common/pom.xml exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

### 4. Run the engagement consumer (terminal 1)

Run from the **module directory** (Maven cannot resolve `spring-boot:` from the reactor root):

```bash
cd streamsocial-engagement-consumer
mvn spring-boot:run
```

Or:

```bash
java -jar streamsocial-engagement-consumer/target/streamsocial-engagement-consumer.jar
```

### 5. Publish test traffic (terminal 2)

```bash
mvn -q -f streamsocial-engagement-consumer/pom.xml exec:java \
  -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator
```

**Expected:** 14 `STRUCTURED_EVENT` lines, then one `STRUCTURED_ERROR ... recovered` for `post-BOOM`.

### Optional: producer API

```bash
cd streamsocial-producer-service
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8081/api/v1/actions \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
```

Expected: HTTP **202** with `eventId`, `topic`, `partition`, `offset`.

Raw producer (no Spring):

```bash
mvn -q -f streamsocial-producer-service/pom.xml exec:java \
  -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo
```

## Tests

```bash
mvn -pl streamsocial-common,streamsocial-producer-service,streamsocial-engagement-consumer -am test
```

Integration tests need Docker (Testcontainers).

## Lesson materials

Step-by-step guides live under `docs/day01/` … `docs/day05/` (`guide.md`, `article.md`, diagrams).

## Notes

- Day 5 uses `DefaultErrorHandler` (Spring Kafka 3.x replacement for the removed `SeekToCurrentErrorHandler`).
- No consumer-group scaling or dead-letter topic yet — Day 6 / Day 25.
- No API keys or cloud credentials are required for this snapshot.
