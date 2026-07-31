# StreamSocial

The cumulative codebase for **Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition**. Every lesson in the course is a commit on top of this same Maven reactor. This snapshot is current through **Day 4**.

## Primary path

```bash
./start.sh   # checks prerequisites, builds, tests, brings up infra + all services, runs today's demo, prints the dashboard URL
./stop.sh    # stops everything start.sh started, cleans Maven output
./stop.sh --wipe   # also removes Docker volumes
```

Both scripts are idempotent — safe to re-run. `start.sh` never halts on a missing later-lesson component; it only orchestrates what exists in this snapshot.

## What exists as of Day 4

| Module | Responsibility |
|---|---|
| `streamsocial-common` | Day 1: `DomainEvent` sealed hierarchy (8 event records with Bean Validation). Day 3: idempotent `AdminClient` topic bootstrap (`TopicBootstrap`). |
| `streamsocial-infra` | Day 2: `docker-compose.yml` for a 3-broker KRaft cluster (ports 9092–9094). |
| `streamsocial-producer-service` | Day 4: Spring Boot app, `POST /api/posts`, pooled (producer-per-thread) `KafkaTemplate` sending `PostCreated` events keyed by `userId`. Port 8082. |
| `streamsocial-dashboard` | Day 4: Spring Boot app, live browser dashboard fed by a raw `kafka-clients` `Consumer` on `user-actions`, pushed to the page over Server-Sent Events. Port 8080. |

`user-actions` and `content-interactions` are created at **local-demo scale** (12/6 partitions) by `start.sh` for a fast local run — the code's documented production default is 1000/500 partitions (see the Day 3/4 articles for the math).

## Manual per-module commands

```bash
# Build + unit test everything (no broker required)
mvn -am test

# Integration tests against a real Testcontainers-launched broker (Day 3, Day 4)
mvn -pl streamsocial-common,streamsocial-producer-service -am verify
# Note: some sandboxed Docker setups (e.g. certain WSL2 + Docker Desktop bridges) block
# Testcontainers' Docker detection even though the native `docker` CLI works fine. start.sh
# detects this specific case and falls back to manual verification automatically.

# Run the Day 1 demo directly
mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
    -pl streamsocial-common -Dexec.mainClass=com.streamsocial.common.event.EventTaxonomyDemo \
    -Dexec.classpathScope=test

# Post an event by hand once the cluster + services are up
curl -X POST localhost:8082/api/posts -H "Content-Type: application/json" \
    -d '{"userId":"<any-uuid>","content":"hello StreamSocial"}'
# then open http://localhost:8080 and watch it live
```

## Requirements

- Java 17+ (JDK 21 also works — the reactor targets `--release 17` regardless of the JDK running the build)
- Maven 3.9+
- Docker (required from Day 2 onward)
