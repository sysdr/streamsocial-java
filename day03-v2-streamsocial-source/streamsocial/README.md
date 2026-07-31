# StreamSocial

Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition.
Built incrementally, one Hands On Kafka lesson at a time. This snapshot
contains the repository state through **Day 3: Topics & Partitions
Strategy**.

## Modules in this snapshot

| Module | Introduced | Responsibility |
|---|---|---|
| `streamsocial-common` | Day 1, extended Day 3 | Event taxonomy + idempotent topic provisioning (`TopicBootstrapper`) |
| `streamsocial-infra` | Day 2, extended Day 3 | 3-broker KRaft Kafka cluster + topic verification script |

No producer or consumer publishes onto the cluster yet — that's Day 4,
which is also when `streamsocial-dashboard` is born.

## Quick start (recommended)

```bash
./start.sh
```

Checks prerequisites, builds, runs tests (including the first
Testcontainers-backed `*IT.java`, run via Failsafe under `mvn verify`),
starts the cluster, then provisions topics at **local-demo-scale**
(12/6 partitions) rather than the real production numbers (1000/500) —
see "Local-demo-scale run" below.

```bash
./stop.sh            # stop all containers, clean Maven build output
./stop.sh --wipe     # also delete Docker volumes (full data reset)
```

## Local-demo-scale run

Day 3's real, documented defaults are **1000** partitions for
`user-actions` and **500** for `content-interactions` — the code always
carries these as its production defaults, and the throughput math behind
them is in `docs/day03/article.md`. Creating 1000 partitions on a single
3-broker laptop cluster works, but is slow to bring up and tear down for
a quick check, so `./start.sh` overrides both counts via system property
to **12** and **6** — the same 2:1 ratio, a fraction of the wait. This
override is never silent: the demo prints `*** LOCAL-DEMO-SCALE RUN ***`
whenever it's active, and the production numbers are one scroll away in
the article.

To run the real numbers instead:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-common \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
```

## Manual, step by step

### Build everything

```bash
mvn -pl streamsocial-common -am verify
```

Expected: `BUILD SUCCESS`. `StreamSocialTopicsTest` runs under Surefire
with no Docker needed; `TopicBootstrapperIT` runs under Failsafe during
the `verify` phase and needs Docker running.

### Bring up the cluster and provision topics

```bash
cd streamsocial-infra && ./scripts/start.sh && cd ..
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -pl streamsocial-common \
  -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo \
  -Dstreamsocial.topics.user-actions-partitions=12 \
  -Dstreamsocial.topics.content-interactions-partitions=6
cd streamsocial-infra && ./scripts/list-topics.sh
```

## Ports claimed so far (Appendix B)

No new ports today — Day 3 only creates topics on the Day 2 cluster
(9092–9094).

## What's next

Day 4 is the first producer: a Spring Boot service publishing real
`UserActionEvent`s (built Day 1) onto `user-actions` (created Day 3),
and the first lesson with a live dashboard.
