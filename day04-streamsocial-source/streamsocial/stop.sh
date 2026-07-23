#!/usr/bin/env bash
# StreamSocial - full cleanup for this repository snapshot.
#
# Stops every Docker Compose-based module found in this snapshot and
# cleans Maven build output. Pass --wipe to also delete Docker volumes
# (drops all cluster/broker data, not just stopping containers).
set -euo pipefail
cd "$(dirname "$0")"

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
      (cd "${dir}" && docker compose down $( [ -n "${WIPE_FLAG}" ] && echo "-v" ))
    fi
  done
else
  echo "==> No Docker Compose module in this snapshot - nothing to stop"
fi

pkill -f "streamsocial-producer-service.jar" 2>/dev/null && \
  echo "==> Stopped a lingering streamsocial-producer-service instance" || true

if command -v fuser >/dev/null 2>&1; then
  fuser -k 8081/tcp >/dev/null 2>&1 && \
    echo "==> Freed port 8081" || true
fi

echo "==> Cleaning Maven build artifacts"
mvn -q -pl streamsocial-common,streamsocial-producer-service -am clean

echo "==> Cleanup complete."
