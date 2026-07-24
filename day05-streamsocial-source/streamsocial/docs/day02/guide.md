# Day 2 Guide: Build, Verify, and Demo the Kafka Cluster

*Companion to Day 2: Kafka Cluster Setup. Full source is in the cumulative `day02-streamsocial-source.zip`; this guide walks through what's in it.*

## The idea, in pseudo-code

```
cluster = 3 nodes, each: process.roles = broker + controller
quorum_voters = {1@kafka-1:9093, 2@kafka-2:9093, 3@kafka-3:9093}

start():
    for each node: format storage with shared CLUSTER_ID, join quorum
    wait until all nodes report healthy

describe_status():
    ask any broker: "who is the current controller leader?"
    -> LeaderId, CurrentVoters, LeaderEpoch

failover_demo():
    leader = describe_status().LeaderId
    kill(container_for(leader))
    poll a surviving node's describe_status() until LeaderId changes
    -> failover confirmed
    restart(container_for(leader))  # rejoins as follower
```

Three nodes, one shared quorum, one script that proves the quorum survives losing a member.

## Step 1 — Start the cluster

```bash
cd streamsocial/streamsocial-infra
./scripts/start.sh
```

**Expected output:**
```
==> Starting StreamSocial Kafka cluster (3 brokers, KRaft mode)
==> Waiting for all three brokers to report healthy...
    streamsocial-kafka-1: healthy
    streamsocial-kafka-2: healthy
    streamsocial-kafka-3: healthy
==> Cluster is up. Bootstrap servers: localhost:29092,localhost:29093,localhost:29094
```

First run pulls the `confluentinc/cp-kafka:7.7.1` image, so expect a longer wait the very first time. Subsequent runs reuse the cached image.

## Step 2 — Verify brokers and quorum

```bash
./scripts/verify-cluster.sh
```

**Expected output:** an API version response from each broker (proves each one accepts client connections), then a `describe --status` block. Confirm:
- `CurrentVoters` lists node IDs `1`, `2`, `3`
- Exactly one `LeaderId` is reported

If any broker fails the health check, check its logs:

```bash
docker compose -f streamsocial-infra/docker-compose.yml logs kafka-2
```

A common first-run cause is insufficient Docker memory — bump Docker Desktop's allocation to at least 4GB and retry.

## Step 3 — Demo controller failover (today's challenge)

```bash
./scripts/demo-failover.sh
```

**Expected output:** the current quorum status, a line identifying and killing the active controller's container, then a second `describe --status` block with a **different** `LeaderId` than the first. The script restarts the killed container automatically at the end.

Run `verify-cluster.sh` again afterward — all three nodes should show healthy and back in the voter set.

## Step 4 — Stop the cluster

```bash
./scripts/stop.sh
```

Data volumes persist by default, so a later `start.sh` picks the cluster back up with the same metadata. Use `./scripts/stop.sh --wipe` to delete the volumes and force a clean re-format on next start.

## Homework

Modify `docker-compose.yml` to run a **5-broker** cluster instead of 3, updating `KAFKA_CONTROLLER_QUORUM_VOTERS` and adding `kafka-4` and `kafka-5` service definitions.

Implementation checklist:
1. Extend the `x-kafka-common` anchor's `KAFKA_CONTROLLER_QUORUM_VOTERS` to list all 5 node IDs.
2. Add `kafka-4` and `kafka-5` service blocks following the existing three as a template — new `KAFKA_NODE_ID`, new host port, new named volume.
3. Update `start.sh`'s health-check loop to include the two new services.
4. Run the failover demo against the 5-node cluster and kill **two** nodes in a row (not just one) — with 5 voters, a majority is 3, so the cluster should survive losing 2.

## Solution hints

- The `CLUSTER_ID` stays the same across all 5 nodes — it identifies the cluster, not a specific node.
- Each new broker needs a unique `KAFKA_NODE_ID` and a unique external port for `PLAINTEXT_HOST` — reusing a port collides with an existing container.
- Killing 2 out of 5 should still leave a working quorum (3 remaining voters, still a majority); killing 3 out of 5 should not — try that too and observe the cluster's metadata operations stall until a voter comes back, which is the concrete meaning of "quorum lost."
- If `describe --status` hangs after killing too many nodes, that's expected behavior, not a bug — restart enough nodes to restore a majority and it recovers on its own.
