#!/usr/bin/env bash
# Day 2 challenge: verify all three brokers are up and inspect the
# controller quorum via kafka-metadata-quorum.
set -euo pipefail

echo "==> Broker API version check (proves each broker accepts client requests)"
for service in kafka-1 kafka-2 kafka-3; do
  echo "--- ${service} ---"
  # PLAINTEXT binds to the container hostname, not 127.0.0.1.
  # Avoid pipefail+head SIGPIPE by capturing then slicing.
  out="$(docker exec "streamsocial-${service}" kafka-broker-api-versions \
    --bootstrap-server "${service}:19092" 2>/dev/null || true)"
  echo "${out}" | head -1
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
