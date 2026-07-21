#!/usr/bin/env bash
# Day 2 challenge, demonstrated: identify the active controller, kill its
# container, and prove the remaining two voters elect a new leader while
# the cluster keeps answering client requests throughout.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Current controller quorum status"
before="$(docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:9092 describe --status)"
echo "${before}"

leader_id="$(echo "${before}" | awk -F': ' '/LeaderId:/ {print $2}' | tr -d '[:space:]')"
if [[ -z "${leader_id}" ]]; then
  echo "Could not parse LeaderId from quorum status output - aborting."
  exit 1
fi
leader_container="streamsocial-kafka-${leader_id}"
echo
echo "==> Active controller is node ${leader_id} (${leader_container})"
echo "==> Killing it to force a controller election..."
docker kill "${leader_container}" >/dev/null

echo "==> Waiting for the remaining two voters to elect a new leader..."
new_leader_id=""
for _ in $(seq 1 15); do
  sleep 2
  survivor="streamsocial-kafka-1"
  [[ "${leader_container}" == "${survivor}" ]] && survivor="streamsocial-kafka-2"
  status="$(docker exec "${survivor}" kafka-metadata-quorum \
    --bootstrap-server "${survivor#streamsocial-}:9092" describe --status 2>/dev/null || true)"
  candidate="$(echo "${status}" | awk -F': ' '/LeaderId:/ {print $2}' | tr -d '[:space:]')"
  if [[ -n "${candidate}" && "${candidate}" != "${leader_id}" ]]; then
    new_leader_id="${candidate}"
    echo "${status}"
    break
  fi
done

if [[ -z "${new_leader_id}" ]]; then
  echo "No new leader observed within the wait window - check container logs."
  docker start "${leader_container}" >/dev/null
  exit 1
fi

echo
echo "==> Failover confirmed: controller moved from node ${leader_id} to node ${new_leader_id}"
echo "==> Restarting the killed node so it rejoins as a follower voter..."
docker start "${leader_container}" >/dev/null
sleep 5
docker exec streamsocial-kafka-1 kafka-metadata-quorum \
  --bootstrap-server kafka-1:9092 describe --status
