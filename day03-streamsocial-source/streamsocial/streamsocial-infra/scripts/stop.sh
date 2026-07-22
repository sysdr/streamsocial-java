#!/usr/bin/env bash
# Day 2 - stop the cluster. Data volumes persist by default so restarting
# doesn't lose the cluster's metadata log; pass --wipe to drop everything
# and re-format from scratch on next start.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--wipe" ]]; then
  echo "==> Stopping cluster and removing data volumes"
  docker compose down -v
else
  echo "==> Stopping cluster (data volumes preserved)"
  docker compose down
fi
