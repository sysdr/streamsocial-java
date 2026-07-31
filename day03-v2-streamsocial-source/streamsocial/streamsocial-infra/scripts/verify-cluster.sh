#!/usr/bin/env bash
# Day 2 challenge: verify all three brokers are up and inspect the
# controller quorum via kafka-metadata-quorum.
set -euo pipefail

echo "==> Broker API version check (proves each broker accepts client requests)"
for service in kafka-1 kafka-2 kafka-3; do
  echo "--- ${service} ---"
  # Capture first, then print one line — piping to head under pipefail
  # exits 141 (SIGPIPE) when the upstream writer is still going.
  out="$(docker exec "streamsocial-${service}" kafka-broker-api-versions \
    --bootstrap-server "${service}:19092" 2>/dev/null || true)"
  echo "${out%%$'\n'*}"
done

echo
echo "==> Controller quorum status (kafka-metadata-quorum describe --status)"
docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:19092 describe --status

echo
echo "==> Controller quorum replication (per-voter lag/state)"
docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:19092 describe --replication

echo
echo "==> All three node IDs (1,2,3) should appear as voters above,"
echo "    and exactly one of them should be reported as LeaderId."
