#!/usr/bin/env bash
# StreamSocial - stop Compose stacks and reclaim unused Docker resources.
#
# Usage:
#   ./cleanup.sh           # stop containers, remove volumes, prune unused resources
#   ./cleanup.sh --dry-run # print actions without executing
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

DRY_RUN=0
if [ "${1:-}" == "--dry-run" ]; then
  DRY_RUN=1
fi

run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "[dry-run] $*"
  else
    "$@"
  fi
}

echo "==> [1/4] Stopping StreamSocial Docker Compose modules"
shopt -s nullglob
compose_dirs=()
for compose_file in */docker-compose.yml; do
  compose_dirs+=("$(dirname "$compose_file")")
done
shopt -u nullglob

if [ "${#compose_dirs[@]}" -eq 0 ]; then
  echo "    No docker-compose.yml modules found"
else
  for dir in "${compose_dirs[@]}"; do
    echo "    Stopping ${dir}"
    if [ -x "${dir}/scripts/stop.sh" ]; then
      if [ "$DRY_RUN" -eq 1 ]; then
        echo "[dry-run] (cd ${dir} && ./scripts/stop.sh --wipe)"
      else
        (cd "${dir}" && ./scripts/stop.sh --wipe)
      fi
    else
      run docker compose -f "$(pwd)/${dir}/docker-compose.yml" down -v --remove-orphans
    fi
  done
fi

echo "==> [2/4] Removing Maven target directories"
if [ "$DRY_RUN" -eq 1 ]; then
  find . -type d -name target -print
else
  find . -type d -name target -prune -exec rm -rf {} +
  if command -v mvn >/dev/null 2>&1; then
    mvn -q -pl streamsocial-common -am clean 2>/dev/null || true
  fi
fi

echo "==> [3/4] Removing unused Docker resources"
# Stopped containers, unused networks, dangling images, unused build cache
run docker container prune -f
run docker network prune -f
run docker image prune -f
run docker builder prune -f
# Project volumes already removed via --wipe / down -v; prune leftover unused volumes
run docker volume prune -f

echo "==> [4/4] Docker disk usage after cleanup"
if [ "$DRY_RUN" -eq 1 ]; then
  echo "[dry-run] docker system df"
else
  docker system df || true
fi

echo "==> Cleanup complete."
