#!/usr/bin/env bash
# StreamSocial - cleanup.sh
# Stop Spring services and Docker Compose infra, then prune unused Docker resources.
# Idempotent — safe to re-run.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

log() { printf '\n[cleanup.sh] %s\n' "$1"; }

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

log "Stopping Spring Boot services..."
stop_spring_app "streamsocial-producer-service" "streamsocial-producer-service/.producer.pid"
stop_spring_app "streamsocial-dashboard" "streamsocial-dashboard/.dashboard.pid"

COMPOSE_FILE="$ROOT_DIR/streamsocial-infra/docker-compose.yml"
if command -v docker >/dev/null 2>&1 && [ -f "$COMPOSE_FILE" ]; then
  log "Stopping Docker Compose infra (including volumes)..."
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
else
  log "Docker Compose infra not available - skipping compose down."
fi

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  log "Removing unused Docker resources (containers, networks, dangling images)..."
  docker system prune -f
  log "Removing unused Docker volumes..."
  docker volume prune -f
else
  log "Docker unavailable - skipping prune."
fi

log "Removing local logs and Maven target directories..."
rm -f streamsocial-producer-service/producer.log \
      streamsocial-dashboard/dashboard.log \
      streamsocial-producer-service/.producer.pid \
      streamsocial-dashboard/.dashboard.pid
find "$ROOT_DIR" -type d -name target -prune -exec rm -rf {} + 2>/dev/null || true

log "cleanup.sh complete."
