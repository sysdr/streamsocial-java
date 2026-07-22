# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
Built incrementally, one Hands On Kafka lesson at a time. This archive
contains the repository state through **Day 1: Event-Driven Architecture
Fundamentals**.

## Modules in this snapshot

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1 | Shared event taxonomy (`DomainEvent` and its three subtypes) |

No Kafka broker exists in this snapshot yet — that's Day 2. Today is about
getting the event shapes right before anything gets published anywhere.

## Prerequisites

- Java 17+
- Maven 3.9+
- Internet access to Maven Central for dependency resolution (first build only)

## Build

```bash
cd streamsocial
mvn -q -pl streamsocial-common -am verify
```

Expected: `BUILD SUCCESS`, 8 tests run, 0 failures.

## Run the demo

```bash
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo
```

Expected output: the full 10-event-type catalog grouped by category, one
constructed instance per category, then a deliberately malformed
`UserActionEvent` (blank `userId`) being rejected by Bean Validation with
the violated property and message printed to the console.

## Run just the tests

```bash
mvn -q -pl streamsocial-common -am test
```

## Lesson materials

Full article, step-by-step guide, and diagrams for each lesson live under `docs/<lesson>/`. This snapshot includes `docs/day01/`.

## What's next

Day 2 adds `streamsocial-infra` with a 3-broker KRaft Kafka cluster in
Docker Compose. Day 4 is the first lesson that actually publishes a
`UserActionEvent` built today onto a real topic.
