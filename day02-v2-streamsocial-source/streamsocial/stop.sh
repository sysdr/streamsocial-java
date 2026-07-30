#!/usr/bin/env bash
# StreamSocial - full cleanup for this repository snapshot.
#
# Stops every Docker Compose-based module found in this snapshot and
# cleans Maven build output. Pass --wipe to also delete Docker volumes.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

WIPE_FLAG=""
if [ "${1:-}" == "--wipe" ]; then
  WIPE_FLAG="--wipe"
fi

shopt -s nullglob
compose_dirs=()
for compose_file in */docker-compose.yml; do
  compose_dirs+=("$(dirname "$compose_file")")
done
shopt -u nullglob

if [ "${#compose_dirs[@]}" -gt 0 ]; then
  echo "==> Stopping Docker Compose module(s): ${compose_dirs[*]}"
  for dir in "${compose_dirs[@]}"; do
    if [ -x "${dir}/scripts/stop.sh" ]; then
      (cd "${dir}" && ./scripts/stop.sh ${WIPE_FLAG})
    else
      docker compose -f "$(pwd)/${dir}/docker-compose.yml" down $( [ -n "${WIPE_FLAG}" ] && echo "-v" )
    fi
  done
else
  echo "==> No Docker Compose module in this snapshot - nothing to stop"
fi

echo "==> Cleaning Maven build artifacts"
mvn -q -pl streamsocial-common -am clean

echo "==> Cleanup complete."
