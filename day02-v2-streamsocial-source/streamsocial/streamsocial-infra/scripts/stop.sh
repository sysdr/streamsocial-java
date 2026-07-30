#!/usr/bin/env bash
# Day 2 - stop the cluster. Data volumes persist by default so
# restarting doesn't lose the cluster's metadata log; pass --wipe to
# drop everything and re-format from scratch on next start.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if [[ "${1:-}" == "--wipe" ]]; then
  echo "==> Stopping cluster and removing data volumes"
  docker compose -f "${COMPOSE_FILE}" down -v
else
  echo "==> Stopping cluster (data volumes preserved)"
  docker compose -f "${COMPOSE_FILE}" down
fi
