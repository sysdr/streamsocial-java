# StreamSocial

The cumulative codebase for **Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition**. Every lesson in the course is a commit on top of this same Maven reactor. This snapshot is current through **Day 1**.

## Primary path

```bash
./start.sh   # checks prerequisites, builds, tests, brings up any infra/dashboard that exists yet, runs today's demo
./stop.sh    # stops everything start.sh started, cleans Maven output
./stop.sh --wipe   # also removes Docker volumes
./cleanup.sh       # stop compose stacks + prune unused Docker resources; removes target/
./cleanup.sh --wipe --prune-images  # also wipe volumes and unused images
```

Both scripts are idempotent — safe to re-run.

## What exists as of Day 1

| Module | Responsibility |
|---|---|
| `streamsocial-common` | `DomainEvent` sealed hierarchy: `UserActionEvent` (`PostCreated`, `PostDeleted`, `ProfileUpdated`), `ContentInteractionEvent` (`PostLiked`, `PostShared`, `PostCommented`), `SystemEvent` (`ServiceHealthChanged`, `RebalanceTriggered`) — all immutable records with Jakarta Bean Validation constraints. |

No Kafka broker, Docker infra, or dashboard exists yet — those arrive Day 2, Day 2, and Day 4 respectively. `start.sh` detects their absence and skips those steps rather than failing.

## Manual per-module commands

```bash
# Build + test just streamsocial-common
mvn -pl streamsocial-common -am verify

# Run the Day 1 demo directly
mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
    -pl streamsocial-common \
    -Dexec.mainClass=com.streamsocial.common.event.EventTaxonomyDemo \
    -Dexec.classpathScope=test
```

## Requirements

- Java 17+ (JDK 21 also works — the reactor targets `--release 17` regardless of the JDK running the build)
- Maven 3.9+
- Docker (not required until Day 2)
