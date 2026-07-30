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

if [ -d "streamsocial-dashboard" ] && [ -f "streamsocial-dashboard/.dashboard.pid" ]; then
  PID="$(cat streamsocial-dashboard/.dashboard.pid)"
  if kill -0 "$PID" >/dev/null 2>&1; then
    log "Stopping streamsocial-dashboard (pid $PID)..."
    kill "$PID" || true
  fi
  rm -f streamsocial-dashboard/.dashboard.pid
else
  log "No running dashboard process recorded - skipping."
fi

if command -v docker >/dev/null 2>&1 && [ -f "streamsocial-infra/docker-compose.yml" ]; then
  log "Stopping Docker Compose infra..."
  if [ "$WIPE" = true ]; then
    (cd streamsocial-infra && docker compose down -v)
  else
    (cd streamsocial-infra && docker compose down)
  fi
else
  log "No Docker infra module in this snapshot yet - skipping."
fi

log "Cleaning Maven build output..."
mvn -q clean

log "stop.sh complete.$( [ "$WIPE" = true ] && echo ' Docker volumes wiped.' )"
