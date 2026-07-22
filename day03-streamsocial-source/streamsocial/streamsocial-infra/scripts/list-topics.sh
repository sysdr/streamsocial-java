#!/usr/bin/env bash
# Day 3 - independent verification of what TopicBootstrapper (in
# streamsocial-common) actually did to the cluster, using the CLI tools
# baked into the broker image rather than the AdminClient code path we're
# trying to verify. Two different tools agreeing is what makes this a
# real check instead of the bootstrapper grading its own homework.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Topics on the cluster"
docker exec streamsocial-kafka-1 kafka-topics \
  --bootstrap-server kafka-1:9092 --list

echo
echo "==> Partition detail for user-actions and content-interactions"
for topic in user-actions content-interactions; do
  echo "--- ${topic} ---"
  docker exec streamsocial-kafka-1 kafka-topics \
    --bootstrap-server kafka-1:9092 --describe --topic "${topic}" \
    | head -1
done
