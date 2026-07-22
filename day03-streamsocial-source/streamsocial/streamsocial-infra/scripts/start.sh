#!/usr/bin/env bash
# Day 2 - bring up the 3-broker KRaft cluster and wait until every
# broker reports healthy before handing control back.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Starting StreamSocial Kafka cluster (3 brokers, KRaft mode)"
docker compose up -d

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
    echo "FAILED to become healthy - check 'docker compose logs ${service}'"
    exit 1
  fi
done

echo "==> Cluster is up. Bootstrap servers: localhost:29092,localhost:29093,localhost:29094"
echo "==> Next: ./scripts/verify-cluster.sh"
