#!/usr/bin/env bash
# StreamSocial - start.sh
# Orchestrates whatever exists in the repository as of the current lesson snapshot.
# Safe to re-run: each step checks state before acting. Never halts on a missing
# later-lesson component - only orchestrates what exists in this snapshot.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() { printf '\n[start.sh] %s\n' "$1"; }
fail() { echo "[start.sh] ERROR: $1" >&2; exit 1; }

log "Checking prerequisites..."
command -v java >/dev/null 2>&1 || fail "Java 17+ is required but was not found on PATH."
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\..*/\1/')"
[ "$JAVA_MAJOR" -ge 17 ] || fail "Java 17+ is required, found major version $JAVA_MAJOR."
command -v mvn >/dev/null 2>&1 || fail "Maven 3.9+ is required but was not found on PATH."
log "Java $JAVA_MAJOR and Maven present."

DOCKER_PRESENT=false
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  DOCKER_PRESENT=true
fi

log "Building and running unit tests across the reactor (Testcontainers-backed integration tests run separately below)..."
mvn -q -am install -DskipITs=true || fail "Unit test / build failure - fix before continuing."
log "Unit tests passed and all module jars are installed locally."

log "Running Testcontainers-backed integration tests (best-effort: some sandboxes block Testcontainers' Docker detection for reasons unrelated to the code - see Appendix C in the master prompt template)..."
if mvn -q -am verify > /tmp/streamsocial-it.log 2>&1; then
  log "Integration tests passed against a real, Testcontainers-launched broker."
else
  if grep -q "Could not find a valid Docker environment" /tmp/streamsocial-it.log; then
    log "Integration tests skipped: this environment's Docker bridge doesn't expose a Testcontainers-compatible API (see /tmp/streamsocial-it.log). Falling back to manual verification against the live docker-compose cluster below."
  else
    log "Integration tests failed for a reason other than the known Docker-bridge gap - see /tmp/streamsocial-it.log. Continuing with the rest of start.sh, but investigate this before shipping."
  fi
fi

# --- Docker Compose modules: auto-discovered, only acted on if present.
#     Uses an absolute path (-f) rather than `cd` + relative discovery so this works
#     regardless of which directory it's invoked from or how the local docker CLI proxies. ---
COMPOSE_FILE="$ROOT_DIR/streamsocial-infra/docker-compose.yml"
if [ "$DOCKER_PRESENT" = true ] && [ -f "$COMPOSE_FILE" ]; then
  log "Bringing up streamsocial-infra (3-broker KRaft cluster)..."
  docker compose -f "$COMPOSE_FILE" up -d
  log "Waiting for brokers to report healthy..."
  for i in $(seq 1 30); do
    HEALTHY=$(docker compose -f "$COMPOSE_FILE" ps 2>/dev/null | grep -c "healthy" || true)
    if [ "$HEALTHY" -ge 3 ] 2>/dev/null; then break; fi
    sleep 2
  done
else
  log "No Docker infra module in this snapshot yet (or Docker unavailable) - skipping compose step."
fi

# --- Day 3: idempotent topic bootstrap. Runs at local-demo scale (Appendix B rule):
#     the code's documented default is the real curriculum number (1000/500 partitions);
#     this local run overrides to a smaller number so it completes in seconds. ---
if [ "$DOCKER_PRESENT" = true ] && [ -d "streamsocial-common/src/main/java/com/streamsocial/common/topic" ]; then
  log "Bootstrapping topics (local-demo scale: 12/6 partitions - production default is 1000/500, see article)..."
  KAFKA_BOOTSTRAP_SERVERS="localhost:9092,localhost:9093,localhost:9094" \
  USER_ACTIONS_PARTITIONS=12 CONTENT_INTERACTIONS_PARTITIONS=6 TOPIC_REPLICATION_FACTOR=3 \
  mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
      -pl streamsocial-common \
      -Dexec.mainClass=com.streamsocial.common.topic.TopicBootstrap \
      -Dexec.classpathScope=test
fi

# --- Day 4: producer-service ---
if [ -d "streamsocial-producer-service" ]; then
  log "Starting streamsocial-producer-service..."
  nohup mvn -q -pl streamsocial-producer-service spring-boot:run > streamsocial-producer-service/producer.log 2>&1 &
  echo $! > streamsocial-producer-service/.producer.pid
  for i in $(seq 1 30); do
    curl -sf http://localhost:8082/actuator/health >/dev/null 2>&1 && { log "Producer service is UP -> http://localhost:8082"; break; }
    sleep 2
  done
fi

# --- Day 4: dashboard ---
if [ -d "streamsocial-dashboard" ]; then
  log "Starting streamsocial-dashboard..."
  nohup mvn -q -pl streamsocial-dashboard spring-boot:run > streamsocial-dashboard/dashboard.log 2>&1 &
  echo $! > streamsocial-dashboard/.dashboard.pid
  for i in $(seq 1 30); do
    curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1 && { log "Dashboard is UP -> http://localhost:8080"; break; }
    sleep 2
  done
fi

# --- Day 1 demo: event taxonomy + Bean Validation, no infra required ---
if [ -d "streamsocial-common" ]; then
  log "Demo: Day 1 DomainEvent taxonomy + Bean Validation"
  mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
      -pl streamsocial-common \
      -Dexec.mainClass=com.streamsocial.common.event.EventTaxonomyDemo \
      -Dexec.classpathScope=test
fi

# --- Day 4 demo: real posts through the real producer, watched live on the dashboard ---
if [ -f "streamsocial-producer-service/.producer.pid" ] && [ -f "streamsocial-dashboard/.dashboard.pid" ]; then
  log "Demo: posting 5 real events through streamsocial-producer-service..."
  for i in 1 2 3 4 5; do
    curl -s -X POST http://localhost:8082/api/posts \
      -H "Content-Type: application/json" \
      -d "{\"userId\":\"$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)\",\"content\":\"start.sh demo post $i\"}" > /dev/null
  done
  log "Demo: live dashboard snapshot (real Kafka consumption, captured for 3s)..."
  curl -N -s -m 3 http://localhost:8080/api/dashboard/stream | tail -3
  log "Open http://localhost:8080 in a browser to watch it update live."
fi

log "start.sh complete for this snapshot."
