#!/usr/bin/env bash
# StreamSocial - start.sh
# Orchestrates whatever exists in the repository as of the current lesson snapshot.
# Safe to re-run: each step checks state before acting.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() { printf '\n[start.sh] %s\n' "$1"; }

log "Checking prerequisites..."
if ! command -v java >/dev/null 2>&1; then
  echo "Java 17+ is required but was not found on PATH." >&2
  exit 1
fi
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\..*/\1/')"
if [ "$JAVA_MAJOR" -lt 17 ]; then
  echo "Java 17+ is required, found major version $JAVA_MAJOR." >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven 3.9+ is required but was not found on PATH." >&2
  exit 1
fi
log "Java $JAVA_MAJOR and Maven present."

DOCKER_PRESENT=false
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  DOCKER_PRESENT=true
fi

log "Resolving dependencies and building the reactor (mvn -am verify)..."
mvn -q -am verify

log "Build and tests passed for every module in this snapshot."

# --- Docker Compose modules: auto-discovered, only acted on if present ---
if [ "$DOCKER_PRESENT" = true ] && [ -f "streamsocial-infra/docker-compose.yml" ]; then
  log "Found streamsocial-infra/docker-compose.yml - bringing up infra..."
  (cd streamsocial-infra && docker compose up -d)
else
  log "No Docker infra module in this snapshot yet (or Docker unavailable) - skipping compose step."
fi

# --- Dashboard: only started if the module exists in this snapshot ---
if [ -d "streamsocial-dashboard" ]; then
  log "streamsocial-dashboard module found - starting it..."
  nohup mvn -q -pl streamsocial-dashboard spring-boot:run > streamsocial-dashboard/dashboard.log 2>&1 &
  DASHBOARD_PID=$!
  echo "$DASHBOARD_PID" > streamsocial-dashboard/.dashboard.pid
  log "Waiting for dashboard health check..."
  for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
      log "Dashboard is UP -> http://localhost:8080"
      break
    fi
    sleep 2
  done
else
  log "No streamsocial-dashboard module yet in this snapshot (arrives Day 4) - skipping."
fi

# --- Day 1 demo: run the event taxonomy demo directly, no infra required ---
if [ -d "streamsocial-common" ]; then
  log "Running Day 1 demo: DomainEvent taxonomy + Bean Validation"
  mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
      -pl streamsocial-common \
      -Dexec.mainClass=com.streamsocial.common.event.EventTaxonomyDemo \
      -Dexec.classpathScope=test
fi

log "start.sh complete for this snapshot."
