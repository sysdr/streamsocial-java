#!/usr/bin/env bash
# StreamSocial - cleanup.sh
# Stops project containers and removes unused Docker resources.
# Safe to re-run. Does not remove images you still need unless --prune-images is passed.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

PRUNE_IMAGES=false
WIPE_VOLUMES=false
for arg in "${@:-}"; do
  case "$arg" in
    --prune-images) PRUNE_IMAGES=true ;;
    --wipe) WIPE_VOLUMES=true ;;
    -h|--help)
      cat <<'HELP'
Usage: ./cleanup.sh [--wipe] [--prune-images]

  --wipe          Also remove Docker Compose volumes for this project
  --prune-images  Also prune unused Docker images (dangling + unused)
HELP
      exit 0
      ;;
  esac
done

log() { printf '\n[cleanup.sh] %s\n' "$1"; }

# Prefer project stop.sh when present (dashboard + compose + mvn clean)
if [ -x "./stop.sh" ] || [ -f "./stop.sh" ]; then
  log "Running stop.sh to shut down project services..."
  if [ "$WIPE_VOLUMES" = true ]; then
    bash ./stop.sh --wipe || true
  else
    bash ./stop.sh || true
  fi
fi

# Stop and remove compose stacks discovered under this repo
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    while IFS= read -r -d '' compose_file; do
      compose_dir="$(dirname "$compose_file")"
      log "Stopping compose stack in $compose_dir ..."
      if [ "$WIPE_VOLUMES" = true ]; then
        (cd "$compose_dir" && docker compose down -v --remove-orphans) || true
      else
        (cd "$compose_dir" && docker compose down --remove-orphans) || true
      fi
    done < <(find "$ROOT_DIR" -type f \( -name 'docker-compose.yml' -o -name 'compose.yml' \) -print0 2>/dev/null)

    log "Removing stopped containers..."
    docker container prune -f || true

    log "Removing unused networks..."
    docker network prune -f || true

    log "Removing unused volumes..."
    docker volume prune -f || true

    log "Removing build cache..."
    docker builder prune -f || true

    if [ "$PRUNE_IMAGES" = true ]; then
      log "Pruning unused images..."
      docker image prune -af || true
    else
      log "Pruning dangling images only (pass --prune-images for unused images)..."
      docker image prune -f || true
    fi

    log "Docker cleanup finished."
  else
    log "Docker daemon is not reachable - skipping Docker cleanup."
  fi
else
  log "Docker is not installed - skipping Docker cleanup."
fi

# Ensure Maven target dirs are gone even if stop.sh was skipped
log "Removing Maven target directories..."
find "$ROOT_DIR" -type d -name target -prune -exec rm -rf {} + 2>/dev/null || true

log "cleanup.sh complete."
