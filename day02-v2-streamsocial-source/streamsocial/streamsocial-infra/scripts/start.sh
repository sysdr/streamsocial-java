#!/usr/bin/env bash
# Day 2 - bring up the 3-broker KRaft cluster and wait until every
# broker reports healthy before handing control back.
#
# Uses an absolute -f path to the compose file (Appendix C) rather than
# `cd` + bare `docker compose up` - relying on the working directory to
# resolve the compose file breaks the moment the invoking shell's cwd
# doesn't match what the local Docker CLI expects, and forces the
# caller to `cd` for no real benefit even on a normal host.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

echo "==> Starting StreamSocial Kafka cluster (3 brokers, KRaft mode)"
docker compose -f "${COMPOSE_FILE}" up -d

echo "==> Waiting for all three brokers to report healthy..."
for service in kafka-1 kafka-2 kafka-3; do
  container="streamsocial-${service}"
  echo -n "    ${container}: "
  for _ in $(seq 1 30); do
    status="$(docker inspect --format='{{.State.Health.Status}}' "${container}" 2>/dev/null || echo "starting")"
    if [[ "${status}" == "healthy" ]]; then
      echo "healthy"
      break
    fi
    sleep 2
  done
  if [[ "${status}" != "healthy" ]]; then
    echo "FAILED to become healthy - check 'docker compose -f ${COMPOSE_FILE} logs ${service}'"
    exit 1
  fi
done

echo "==> Cluster is up. Bootstrap servers: localhost:9092,localhost:9093,localhost:9094"
echo "==> Next: ./scripts/verify-cluster.sh"
