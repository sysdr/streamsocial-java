# Day 2: Kafka Cluster Setup

*Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition, Module 1: Foundation & Core Concepts (Days 1–10)*

## What we'll build today

- A real 3-broker Kafka cluster running in KRaft mode — no Zookeeper anywhere in the stack
- `streamsocial-infra`: Docker Compose, health checks, and scripts, sitting alongside `streamsocial-common` from Day 1
- A controller quorum you can actually kill a node in and watch re-elect itself

Yesterday's event taxonomy has nowhere to go yet. Today it gets somewhere to go.

[DIAGRAM: component-architecture]

## Why KRaft, not Zookeeper

For most of Kafka's life, the cluster's metadata — which broker leads which partition, which topics exist, which consumer groups own what — lived in a separate Zookeeper ensemble. That meant running and operating two distributed systems to get one working Kafka cluster: Kafka itself, and Zookeeper underneath it holding the metadata Kafka depended on to function at all.

KRaft (Kafka Raft) removes that second system. Kafka's own brokers — or a designated subset of them — now run a Raft consensus protocol directly to agree on cluster metadata. One system, one thing to operate, one thing to reason about when something goes wrong. This isn't a minor internal refactor; it's the reason a 3-node cluster today is three Kafka containers instead of three Kafka containers plus three (or five) Zookeeper containers watching over them.

## Broker roles and the controller quorum

In KRaft mode, every node runs with a `process.roles` setting of `broker`, `controller`, or both. A **broker** serves client reads and writes — the thing producers and consumers actually talk to. A **controller** participates in the metadata Raft quorum — deciding partition leadership, tracking which brokers are alive, and persisting the cluster's source of truth.

For a cluster this size, every node runs both roles at once — `broker,controller` — which is the standard shape for development and teaching clusters. Larger production deployments typically split dedicated controller-only nodes out from the brokers serving traffic, which Day 20 comes back to when replication and ISR management get more nuanced.

What matters today is the **controller quorum**: the set of nodes voting on metadata changes via `controller.quorum.voters`. With three voters, any two form a majority — which is exactly what makes today's challenge possible. Kill one voter, including the current leader, and the remaining two keep the cluster's metadata consistent and elect a new active controller among themselves.

[DIAGRAM: flowchart]

## Where this cluster fits

This isn't a generic "spin up Kafka" exercise — it's specifically the cluster Day 3 creates topics on, Day 4's producer publishes `UserActionEvent`s (from Day 1) to at up to 5M events/second, and every module for the rest of the course connects to. `streamsocial-infra` has no Java code in it — it's Docker Compose and shell scripts — which is why it isn't a Maven `<module>` the way `streamsocial-common` is. It's still real infrastructure the rest of the system depends on; it just doesn't compile.

The docker-compose file uses a YAML anchor (`x-kafka-common`) so the three broker definitions share one block of Kafka configuration instead of three copy-pasted ones drifting out of sync as the course progresses — worth noticing, because Day 35 and Day 56 both come back to edit this same file and having one source of truth for shared config matters more with every lesson added on top.

## What actually happens when you kill the leader

[DIAGRAM: state-machine]

Today's challenge — verify controller failover — has three parts:

1. **Find the active controller.** `kafka-metadata-quorum.sh describe --status` reports a `LeaderId` among the three voters. That's the node currently deciding metadata changes.
2. **Kill it.** Not a graceful shutdown — `docker kill`, the same as pulling power on a physical machine. This is the failure mode worth testing, because graceful shutdowns are the easy case.
3. **Watch the survivors.** With node availability down to two out of three, the surviving voters still hold a majority. They detect the missing leader, run a new election, and one of them becomes the new `LeaderId` — typically within a few seconds, well inside what a production SLA would tolerate.

The cluster's client-facing brokers keep answering requests through all of this, because broker availability and controller leadership are separate concerns — losing the active controller doesn't take the cluster offline, it just changes who's writing the metadata log.

## Success criteria for today

`./scripts/verify-cluster.sh` shows all three brokers healthy and all three node IDs as controller quorum voters. `./scripts/demo-failover.sh` shows a `LeaderId` change after the active controller's container is killed, and the killed node rejoining as a follower once restarted. If you can point at the output and explain why two-out-of-three is enough to keep the cluster's metadata consistent, you understand today's lesson — not just ran the script.

Tomorrow: turning this cluster into somewhere Day 1's events can actually live, by creating `user-actions` and `content-interactions` with partition counts sized for StreamSocial's real traffic.
