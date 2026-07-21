#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==> Stopping StreamSocial Java processes..."
pkill -f 'com.streamsocial.common.demo.EventTaxonomyDemo' 2>/dev/null || true
pkill -f 'streamsocial' 2>/dev/null || true

echo "==> Stopping Docker Compose services (if present)..."
if command -v docker >/dev/null 2>&1; then
  if [ -f docker-compose.yml ]; then
    docker compose down -v --remove-orphans 2>/dev/null || docker-compose down -v --remove-orphans 2>/dev/null || true
  fi

  echo "==> Stopping all running containers..."
  RUNNING="$(docker ps -q 2>/dev/null || true)"
  if [ -n "$RUNNING" ]; then
    docker stop $RUNNING
  else
    echo "    No running containers."
  fi

  echo "==> Removing unused Docker resources..."
  docker container prune -f
  docker network prune -f
  docker volume prune -f
  docker image prune -f
else
  echo "    Docker not installed; skipping container cleanup."
fi

echo "==> Removing Maven target directories..."
find "$SCRIPT_DIR" -type d -name target -prune -exec rm -rf {} +

echo "==> Cleanup complete."
