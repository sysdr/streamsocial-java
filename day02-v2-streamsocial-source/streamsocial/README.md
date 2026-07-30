# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
Built incrementally, one Hands On Kafka lesson at a time. This snapshot
contains the repository state through **Day 2: Kafka Cluster Setup**.

## Modules in this snapshot

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1 | Shared event taxonomy (`DomainEvent` and its three subtypes) |
| `streamsocial-infra` | Day 2 | 3-broker KRaft Kafka cluster (Docker Compose, no Java code) |

No topics exist on the cluster yet — that's Day 3. No producer or
consumer publishes onto it yet — that's Day 4. `streamsocial-dashboard`
doesn't exist yet either — it's born Day 4, once there's real running
behavior worth watching live.

## Quick start (recommended)

```bash
./start.sh
```

Checks prerequisites, resolves Maven dependencies, builds, runs tests,
starts every Docker Compose module in this snapshot (the Kafka cluster),
and runs today's demo + cluster verification end to end.

```bash
./stop.sh            # stop all containers, clean Maven build output
./stop.sh --wipe     # also delete Docker volumes (full data reset)
```

## Manual, step by step

### Build the event taxonomy

```bash
mvn -q -pl streamsocial-common -am verify
```

Expected: `BUILD SUCCESS`, 8 tests run, 0 failures.

```bash
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo
```

Expected: the 10-event-type catalog, three constructed sample events, and
one deliberately malformed event rejected by Bean Validation.

### Bring up the Kafka cluster

```bash
cd streamsocial-infra
./scripts/start.sh
./scripts/verify-cluster.sh
./scripts/demo-failover.sh
./scripts/stop.sh
```

Expected: three healthy brokers, a controller quorum status showing all
three node IDs as voters, and a failover demo showing the `LeaderId`
change after the active controller's container is killed. Full detail in
`streamsocial-infra/README.md`.

## Ports claimed so far (Appendix B)

| Port | Owner |
|---|---|
| 9092–9094 | Kafka brokers |
| 8081 | Schema Registry (reserved, unused until Day 27) |

## What's next

Day 3 creates the `user-actions` (1000 partitions) and
`content-interactions` (500 partitions) topics on this cluster. Day 4 is
the first lesson that publishes an actual `UserActionEvent` (built Day 1)
onto one of them, and the first lesson with a live dashboard panel.
