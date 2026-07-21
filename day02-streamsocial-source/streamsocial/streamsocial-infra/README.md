# streamsocial-infra

Day 2: the 3-broker KRaft Kafka cluster every service from Day 4 onward
connects to. No Java code lives here — this module is Docker Compose and
shell scripts, so it isn't registered in the root `pom.xml`'s `<modules>`
list the way `streamsocial-common` is; there's nothing for Maven to build.

## Why 3 brokers, combined mode

Three nodes is the minimum that makes controller quorum failover
demonstrable: with `KAFKA_CONTROLLER_QUORUM_VOTERS` listing all three,
losing any one node still leaves a 2-node majority able to elect a new
active controller. Each node runs `broker,controller` combined roles,
which is standard for a small cluster — Day 20 discusses when you'd split
dedicated controller-only nodes out in a larger deployment.

## Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin), 4GB+ RAM available to Docker
- No Java/Maven needed for this lesson specifically

## Start the cluster

```bash
./scripts/start.sh
```

**Expected output:** all three `streamsocial-kafka-N` containers reported
`healthy`, ending with the bootstrap server list
(`localhost:29092,localhost:29093,localhost:29094`).

## Verify the cluster and controller quorum

```bash
./scripts/verify-cluster.sh
```

**Expected output:** an API version line from each of the three brokers,
then `kafka-metadata-quorum.sh describe --status` output showing
`CurrentVoters` containing node IDs `1,2,3` and exactly one `LeaderId`.

## Demo: controller failover (today's challenge)

```bash
./scripts/demo-failover.sh
```

This identifies whichever node is the current active controller, kills
that container outright, waits for the surviving two voters to elect a
new leader, prints the new quorum status confirming a different
`LeaderId`, then restarts the killed node so it rejoins as a follower.

**Expected output:** two `describe --status` blocks — the first showing
the original leader, the second showing a new `LeaderId` after the kill,
with a line confirming which node ID took over.

## Stop the cluster

```bash
./scripts/stop.sh          # containers stop, data volumes kept
./scripts/stop.sh --wipe   # containers stop, data volumes deleted too
```

## What's next

Day 3 creates the `user-actions` and `content-interactions` topics on
this cluster and works out their partition counts. Day 4 is the first
lesson that publishes an actual `UserActionEvent` (built Day 1) onto one
of them.
