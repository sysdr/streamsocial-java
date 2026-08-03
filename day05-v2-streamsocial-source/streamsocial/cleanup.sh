#!/usr/bin/env bash
# StreamSocial - stop project containers and free unused Docker resources.
#
# Stops the StreamSocial Kafka Compose stack, kills local Spring Boot
# service processes, removes Maven target/ build output, then prunes
# unused Docker containers, networks, and dangling images.
#
# Usage:
#   ./cleanup.sh           # stop + prune unused resources (volumes kept)
#   ./cleanup.sh --wipe    # also delete project Compose volumes
#   ./cleanup.sh --all     # --wipe plus docker system prune --volumes
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

WIPE=0
PRUNE_VOLUMES=0
for arg in "${@:-}"; do
  case "${arg}" in
    --wipe) WIPE=1 ;;
    --all)  WIPE=1; PRUNE_VOLUMES=1 ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
  esac
done

echo "==> Stopping StreamSocial Spring Boot / Java service processes"
pkill -f "streamsocial-dashboard" 2>/dev/null && echo "    stopped dashboard" || true
pkill -f "streamsocial-producer-service" 2>/dev/null && echo "    stopped producer" || true
pkill -f "streamsocial-engagement-consumer" 2>/dev/null && echo "    stopped engagement-consumer" || true
pkill -f "spring-boot:run" 2>/dev/null && echo "    stopped spring-boot:run" || true

echo "==> Stopping Docker Compose module(s)"
shopt -s nullglob
compose_dirs=()
for compose_file in */docker-compose.yml; do
  compose_dirs+=("$(dirname "$compose_file")")
done
shopt -u nullglob

if [ "${#compose_dirs[@]}" -eq 0 ]; then
  echo "    no docker-compose.yml modules found"
else
  for dir in "${compose_dirs[@]}"; do
    if [ -x "${dir}/scripts/stop.sh" ]; then
      if [ "${WIPE}" -eq 1 ]; then
        (cd "${dir}" && ./scripts/stop.sh --wipe)
      else
        (cd "${dir}" && ./scripts/stop.sh)
      fi
    else
      if [ "${WIPE}" -eq 1 ]; then
        docker compose -f "$(pwd)/${dir}/docker-compose.yml" down -v
      else
        docker compose -f "$(pwd)/${dir}/docker-compose.yml" down
      fi
    fi
  done
fi

echo "==> Removing Maven target/ directories"
if command -v mvn >/dev/null 2>&1 && [ -f pom.xml ]; then
  mvn -q clean || true
fi
find . -type d -name target -prune -exec rm -rf {} + 2>/dev/null || true

echo "==> Pruning unused Docker resources"
if [ "${PRUNE_VOLUMES}" -eq 1 ]; then
  docker system prune -af --volumes
else
  docker system prune -f
fi

echo "==> Cleanup complete."
