# Day 3 Guide: Build, Test, and Demo Topic Provisioning

*Companion to Day 3: Topics & Partitions Strategy. Source lives in `streamsocial-common/src/main/java/com/streamsocial/common/admin/` and `.../demo/TopicBootstrapDemo.java`; this guide walks through what's there.*

## The idea, in pseudo-code

```
TopicSpec(name, partitions, replicationFactor, configs)

catalog = [
    TopicSpec("user-actions",         partitions=1000, rf=3),
    TopicSpec("content-interactions", partitions=500,  rf=3),
]

bootstrap(desired):
    existing = adminClient.listTopics()
    missing  = desired where name not in existing
    present  = desired where name in existing

    if missing: adminClient.createTopics(missing)

    for spec in present:
        actual = adminClient.describeTopic(spec.name).partitionCount
        if actual != spec.partitions:
            report_mismatch(spec.name, expected=spec.partitions, actual)
        else:
            report_verified(spec.name)

    return created, verified, mismatched
```

Two calls to the cluster — list, then either create or describe — and everything else is comparing what came back against the catalog.

## Step 1 — Make sure the Day 2 cluster is running

```bash
cd streamsocial-infra
./scripts/start.sh
```

If it's already up from Day 2, this is a no-op — `docker compose up -d` on already-running containers just confirms they're healthy.

## Step 2 — Run the bootstrap

```bash
cd ..
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

**Expected output (first run):**
```
Created:            [user-actions, content-interactions]
Already present ok:  []
Mismatched:          []

Run this again right now - it should create nothing the second time.
```

Run the exact same command again:

**Expected output (second run):**
```
Created:            []
Already present ok:  [user-actions, content-interactions]
Mismatched:          []
```

That difference between the two runs is the whole point — same command, different (correct) behavior depending on cluster state.

## Step 3 — Verify independently from the CLI

```bash
cd streamsocial-infra
./scripts/list-topics.sh
```

**Expected output:** both topic names listed, `user-actions` describing with `PartitionCount: 1000`, `content-interactions` with `PartitionCount: 500`. This uses `kafka-topics` directly against the broker, not the Java `AdminClient` code path — two independent tools agreeing is a real check.

## Step 4 — Run the tests

```bash
mvn -q -pl streamsocial-common -am test
```

`StreamSocialTopicsTest` runs with no Docker needed (pure catalog + Bean Validation checks). `TopicBootstrapperIntegrationTest` spins up a real single-node broker via Testcontainers — Docker must be running — and proves idempotency and mismatch detection against that broker, independent of your Day 2 cluster.

**Expected:** `Tests run: 10, Failures: 0, Errors: 0` combined across both test classes.

## Homework

Add a third topic to the catalog: `moderation-events`, sized for an assumed peak of 2M events/sec, using the same 30,000 events/sec-per-partition budget this lesson used for the other two — but this time, don't cap it below the raw math. Justify in a one-line comment why `moderation-events` doesn't need the same treatment `content-interactions` got.

Implementation checklist:
1. Add a `MODERATION_EVENTS` constant to `StreamSocialTopics`, computing the partition count from the stated peak and budget.
2. Add it to `StreamSocialTopics.ALL`.
3. Add a test asserting the catalog grew to 3 entries and the new topic's partition count matches your math.
4. Re-run the bootstrap demo and `list-topics.sh` and confirm the new topic appears with the right partition count.

## Solution hints

- `2,000,000 / 30,000 ≈ 67` — round to a clean number like 64 or 70, your call, just show the arithmetic in the comment rather than picking a bare number.
- `moderation-events` doesn't need capping because it's a `SystemEvent` type (Day 1) keyed by `subjectId` — moderation flags aren't subject to the same viral power-law skew as user-generated content interactions; a flagged post is a flagged post, not something that gets re-flagged a million times by a crowd.
- If your test for the new topic fails with a Bean Validation violation instead of a partition-count mismatch, check that `replicationFactor` is a `short` literal (`(short) 3`), not an `int` — the compiler won't catch this one for you since both are numeric.
