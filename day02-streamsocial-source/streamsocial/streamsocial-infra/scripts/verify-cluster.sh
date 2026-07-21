#!/usr/bin/env bash
# Day 2 challenge: verify all three brokers are up and inspect the
# controller quorum via kafka-metadata-quorum.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Broker API version check (proves each broker accepts client requests)"
for service in kafka-1 kafka-2 kafka-3; do
  echo "--- ${service} ---"
  docker exec "streamsocial-${service}" kafka-broker-api-versions \
    --bootstrap-server localhost:9092 2>/dev/null | head -1
done

echo
echo "==> Controller quorum status (kafka-metadata-quorum.sh describe --status)"
docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:9092 describe --status

echo
echo "==> Controller quorum replication (per-voter lag/state)"
docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:9092 describe --replication

echo
echo "==> All three node IDs (1,2,3) should appear as voters above,"
echo "    and exactly one of them should be reported as LeaderId."
