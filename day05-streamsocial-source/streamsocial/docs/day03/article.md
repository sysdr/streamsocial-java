# Day 3: Topics & Partitions Strategy

*Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition, Module 1: Foundation & Core Concepts (Days 1–10)*

## What we'll build today

- `user-actions` and `content-interactions`, the first two topics on the Day 2 cluster
- `TopicBootstrapper`: an idempotent, `AdminClient`-based provisioning class in `streamsocial-common` — the first code in this repository that talks to Kafka at all
- The actual math behind why one topic gets 1000 partitions and the other gets 500, not a round number picked because it looked reasonable

[DIAGRAM: component-architecture]

## A partition is the unit everything else scales around

A Kafka topic isn't one log — it's however many partitions you give it, each an independently ordered, independently replicated append-only log. That single fact is the reason partition count is a decision you make once, carefully, rather than a config value you bump later without thinking: partition count sets the ceiling on consumer parallelism (Day 6 scales feed-generation workers up to one per partition — no more, because a partition can only be read by one consumer within a group at a time), and it's the unit `min.insync.replicas` and replication actually operate on.

Get it too low and you cap your own future scale-out. Get it too high relative to what a topic's key space can actually spread across, and you pay real costs — more file handles per broker, longer leader-election windows during failover, more replication traffic — for parallelism nothing ever uses.

## The math, not a guess

StreamSocial's platform-wide target is 50M events/second at peak, across every topic. Today's two topics carry an assumed split of that: 30M/sec on `user-actions` (posts, follows, profile updates), 20M/sec on `content-interactions` (likes, comments, shares).

Using a conservative sustained-throughput budget of roughly 30,000 events/sec per partition — small JSON events, replication factor 3, headroom left for consumer fan-out — the arithmetic is direct:

```
user-actions:          30,000,000 / 30,000  ≈ 1000 partitions
content-interactions:  20,000,000 / 30,000  ≈  667 partitions
```

`user-actions` lands on 1000 partitions, matching the math. `content-interactions` gets capped at **500** — deliberately below what the raw division suggests.

[DIAGRAM: flowchart]

## Why content-interactions is capped below the math

This is the part worth sitting with, because it's the opposite of "more partitions is always safer." `user-actions` is keyed by `userId`: with millions of distinct users, traffic spreads close to evenly across however many partitions you give it, so raw partition count converts almost directly into usable parallelism.

`content-interactions` is keyed by `contentId`, and content interaction volume follows a power law — a small number of posts go viral and absorb a wildly disproportionate share of likes, comments, and shares, while most content gets a handful of interactions total. Give that topic 667 or even 1000 partitions and the hot keys still concentrate on however many partitions their hashes land on; the rest of the extra partitions sit close to idle. You've added broker overhead — more replicated logs, more leader elections to manage on failover — without buying any real relief for the actual bottleneck. That bottleneck gets fixed in Day 15 with a smarter `Partitioner`, and later with hot-key salting, not with a bigger partition count today. Capping at 500 here is that engineering judgment made explicit, not a compromise.

## Idempotent, not "run once and hope"

[DIAGRAM: state-machine]

`TopicBootstrapper` is written to be run repeatedly and safely: it lists existing topics first, only submits `createTopics` for the ones actually missing, and — this is the part naive bootstrap scripts skip — **verifies** that topics which already exist actually match the desired partition count, rather than assuming "it exists, good enough." A topic that exists with the wrong partition count doesn't get silently left alone or silently fixed; Kafka can't shrink partitions at all, and growing them later changes which partition a given key hashes to for every message already flowing through the topic, quietly breaking per-key ordering guarantees downstream consumers depend on. That's a decision for a human, so the bootstrapper reports it loudly instead of guessing.

This is also the first code in the repository with a `kafka-clients` dependency — via `Admin`/`AdminClient` only. Nothing here produces or consumes a message; that's Day 4 and Day 5's job.

## Success criteria for today

Run `TopicBootstrapDemo` against the Day 2 cluster and confirm both topics get created. Run it again immediately and confirm the second run creates nothing — both topics report as already present and verified. Then explain, in one sentence each, why `user-actions` got 1000 partitions and why `content-interactions` didn't get the full 667 the raw math suggested.

Tomorrow: the first producer, publishing actual `UserActionEvent`s from Day 1 onto `user-actions` at scale.
