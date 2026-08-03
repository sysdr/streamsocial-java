# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
Built incrementally, one Hands On Kafka lesson at a time. This snapshot
contains the repository state through **Day 5: Engagement Consumer
Development**.

## Modules in this snapshot

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1, extended Day 3 | Event taxonomy + idempotent topic provisioning |
| `streamsocial-infra` | Day 2, extended Day 3 | 3-broker KRaft Kafka cluster + topic verification script |
| `streamsocial-producer-service` | Day 4 | Spring Boot REST facade publishing `UserActionEvent` via one shared `KafkaTemplate` |
| `streamsocial-dashboard` | Day 4, extended Day 5 | Live browser dashboard — `user-actions` and `content-interactions` panels |
| `streamsocial-engagement-consumer` | Day 5 | `@KafkaListener` reading `content-interactions`, `DefaultErrorHandler` retry/recovery |

## Ports (Appendix B)

| Port | Owner |
|---|---|
| 9092–9094 | Kafka brokers |
| 8081 | Schema Registry (reserved, unused until Day 27) |
| 8082 | `streamsocial-producer-service` |
| 8080 | `streamsocial-dashboard` |

`streamsocial-engagement-consumer` has no web server (a worker process,
kept alive by the Kafka listener container's own threads) — no port to
reserve.

## Quick start (recommended)

```bash
./start.sh
```

Builds and tests everything, brings up the cluster, provisions topics,
starts the dashboard and producer (as Day 4 did), then builds and starts
the engagement consumer, publishes test traffic, and shows both the
processed-event and error-recovery log lines — the same traffic also
appears live on the dashboard's new `content-interactions` panel.

```bash
./stop.sh            # stop everything, clean Maven build output
./stop.sh --wipe     # also delete Docker volumes
```

## A correction worth knowing about

The curriculum this course grew out of names `SeekToCurrentErrorHandler`
as today's target class. That class has been deprecated since Spring
Kafka 2.8 and removed entirely in 3.x. This module uses its supported
replacement, `DefaultErrorHandler` — see
`streamsocial-engagement-consumer/README.md` and `docs/day05/article.md`.

## Manual, step by step

```bash
cd streamsocial-infra && ./scripts/start.sh && cd ..
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-common \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo \
  -Dstreamsocial.topics.user-actions-partitions=12 \
  -Dstreamsocial.topics.content-interactions-partitions=6

mvn -pl streamsocial-dashboard -am spring-boot:run &
mvn -pl streamsocial-engagement-consumer -am spring-boot:run &

mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-engagement-consumer \
  -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator
```

Open http://localhost:8080 and watch the `content-interactions` panel
fill in live.

## What's next

Day 6 scales consumer groups and partition assignment strategies —
running many replicas of a feed-generation worker in parallel.
