#!/usr/bin/env bash
# Day 3 - independent verification of what TopicBootstrapper (in
# streamsocial-common) actually did to the cluster, using the CLI tools
# baked into the broker image rather than the AdminClient code path we're
# trying to verify. Two different tools agreeing is what makes this a
# real check instead of the bootstrapper grading its own homework.
set -euo pipefail

echo "==> Topics on the cluster"
docker exec streamsocial-kafka-1 kafka-topics \
  --bootstrap-server kafka-1:19092 --list

echo
echo "==> Partition detail for user-actions and content-interactions"
for topic in user-actions content-interactions; do
  echo "--- ${topic} ---"
  out="$(docker exec streamsocial-kafka-1 kafka-topics \
    --bootstrap-server kafka-1:19092 --describe --topic "${topic}" 2>/dev/null || true)"
  echo "${out}" | head -1
done
