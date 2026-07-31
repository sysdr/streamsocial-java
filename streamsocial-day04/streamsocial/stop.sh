#!/usr/bin/env bash
# StreamSocial - stop.sh
# Full cleanup of whatever start.sh brought up in this snapshot. Idempotent.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

WIPE=false
if [ "${1:-}" = "--wipe" ]; then
  WIPE=true
fi

log() { printf '\n[stop.sh] %s\n' "$1"; }

# spring-boot:run forks a child JVM per app; killing just the recorded Maven pid can leave
# that child running, so each stop is: kill the recorded pid, then sweep by module name.
stop_spring_app() {
  local module="$1"
  local pid_file="$2"
  if [ -f "$pid_file" ]; then
    PID="$(cat "$pid_file")"
    kill "$PID" >/dev/null 2>&1 || true
    rm -f "$pid_file"
  fi
  if pgrep -f "pl $module spring-boot:run" >/dev/null 2>&1; then
    log "Stopping $module..."
    pkill -f "pl $module spring-boot:run" || true
  else
    log "No running $module process found - skipping."
  fi
}

stop_spring_app "streamsocial-producer-service" "streamsocial-producer-service/.producer.pid"
stop_spring_app "streamsocial-dashboard" "streamsocial-dashboard/.dashboard.pid"

COMPOSE_FILE="$ROOT_DIR/streamsocial-infra/docker-compose.yml"
if command -v docker >/dev/null 2>&1 && [ -f "$COMPOSE_FILE" ]; then
  log "Stopping Docker Compose infra..."
  if [ "$WIPE" = true ]; then
    docker compose -f "$COMPOSE_FILE" down -v
  else
    docker compose -f "$COMPOSE_FILE" down
  fi
else
  log "No Docker infra module in this snapshot yet - skipping."
fi

log "Cleaning Maven build output..."
mvn -q clean

log "stop.sh complete.$( [ "$WIPE" = true ] && echo ' Docker volumes wiped.' )"
