# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
Built incrementally, one Hands On Kafka lesson at a time. This snapshot
contains the repository state through **Day 3: Topics & Partitions
Strategy**.

## Modules in this snapshot

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1, extended Day 3 | Event taxonomy (`DomainEvent`) + idempotent topic provisioning (`TopicBootstrapper`) |
| `streamsocial-infra` | Day 2, extended Day 3 | 3-broker KRaft Kafka cluster (Docker Compose) + topic verification script |

No producer or consumer publishes onto the cluster yet — that's Day 4.

## Prerequisites

- Java 17+, Maven 3.9+
- Docker Desktop or Docker Engine + Compose plugin, 4GB+ RAM available to Docker
- Internet access to Maven Central for dependency resolution (first Java build only)

## Quick start (recommended)

```bash
./start.sh
```

One command: checks prerequisites, resolves Maven dependencies, builds,
runs unit + integration tests, starts every Docker Compose module in
this snapshot (just the Kafka cluster today), provisions topics, and
runs each lesson's demo end to end. Safe to re-run.

```bash
./stop.sh            # stop all containers, clean Maven build output
./stop.sh --wipe     # also delete Docker volumes (full data reset)
```

## Manual, step by step

## Build everything

```bash
mvn -q -pl streamsocial-common -am verify
```

Expected: `BUILD SUCCESS`. `StreamSocialTopicsTest` runs with no Docker
needed; `TopicBootstrapperIntegrationTest` spins up a real broker via
Testcontainers, so Docker must be running for the full `verify`.

## Bring up the cluster and provision topics

```bash
cd streamsocial-infra
./scripts/start.sh
cd ..
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
cd streamsocial-infra
./scripts/list-topics.sh
```

Expected: `user-actions` (1000 partitions) and `content-interactions`
(500 partitions) created on the first run, confirmed independently via
`kafka-topics --describe`. Run the bootstrap demo a second time and it
should create nothing — both topics report already present and verified.

Full command reference in `streamsocial-infra/README.md` (cluster) and
`docs/day03/guide.md` (topic provisioning).

## Lesson materials

The step-by-step build/test/demo guide for each lesson lives under
`docs/<lesson>/guide.md`, alongside the lesson article and diagrams. This
snapshot includes `docs/day01/`, `docs/day02/`, and `docs/day03/`.

## What's next

Day 4 is the first producer: a Spring Boot service publishing real
`UserActionEvent`s (built Day 1) onto `user-actions` (created Day 3),
load-tested toward 5M posts/second.
