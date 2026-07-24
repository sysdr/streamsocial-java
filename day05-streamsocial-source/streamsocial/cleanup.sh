#!/usr/bin/env bash
# StreamSocial cleanup: stop app processes + Docker Compose stack,
# remove project volumes, and prune unused Docker resources.
set -euo pipefail
cd "$(dirname "$0")"

WIPE_VOLUMES=1
PRUNE_SYSTEM=1

usage() {
  cat <<'EOF'
Usage: ./cleanup.sh [--keep-volumes] [--no-prune] [-h|--help]

  Stops StreamSocial jars/processes and the Kafka Docker Compose stack,
  deletes project Docker volumes by default, removes Maven target/
  directories, and prunes unused Docker images/networks/build cache.

Options:
  --keep-volumes  Stop containers but keep Kafka data volumes
  --no-prune      Skip docker system/image/volume prune
  -h, --help      Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-volumes) WIPE_VOLUMES=0; shift ;;
    --no-prune) PRUNE_SYSTEM=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

echo "==> Stopping StreamSocial Java processes (if any)"
# Match jar names only — avoid killing this script's shell.
for pattern in \
  'streamsocial-producer-service/target/streamsocial-producer-service.jar' \
  'streamsocial-engagement-consumer/target/streamsocial-engagement-consumer.jar' \
  'streamsocial-producer-service.jar' \
  'streamsocial-engagement-consumer.jar'
do
  pkill -f "${pattern}" 2>/dev/null && echo "    stopped processes matching: ${pattern}" || true
done

if [ -x ./stop.sh ]; then
  if [ "${WIPE_VOLUMES}" -eq 1 ]; then
    echo "==> Stopping Docker Compose stack and wiping volumes"
    ./stop.sh --wipe || true
  else
    echo "==> Stopping Docker Compose stack (keeping volumes)"
    ./stop.sh || true
  fi
elif [ -f streamsocial-infra/docker-compose.yml ]; then
  echo "==> Stopping streamsocial-infra via docker compose"
  if [ "${WIPE_VOLUMES}" -eq 1 ]; then
    (cd streamsocial-infra && docker compose down -v) || true
  else
    (cd streamsocial-infra && docker compose down) || true
  fi
fi

echo "==> Removing Maven target directories"
find . -type d -name target -prune -exec rm -rf {} + 2>/dev/null || true
rm -f /tmp/streamsocial-producer-service.log /tmp/streamsocial-engagement-consumer.log 2>/dev/null || true

if [ "${PRUNE_SYSTEM}" -eq 1 ]; then
  if command -v docker >/dev/null 2>&1; then
    echo "==> Pruning unused Docker resources"
    # Safe, non-interactive prune of dangling/unused resources.
    docker container prune -f || true
    docker network prune -f || true
    docker image prune -f || true
    docker volume prune -f || true
    docker builder prune -f || true
  else
    echo "==> Docker not found — skipping prune"
  fi
fi

echo "==> Cleanup complete."
