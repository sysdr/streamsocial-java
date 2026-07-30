#!/usr/bin/env bash
# StreamSocial - one-command bring-up for this repository snapshot.
#
# Resolves Maven dependencies, builds, runs unit + integration tests,
# starts any Docker Compose-based module found in this snapshot (just
# the Kafka cluster as of today - Day 2), and runs each lesson's demo
# end to end. Safe to re-run: already-healthy pieces are detected and
# left alone rather than restarted from scratch.
#
# Once streamsocial-dashboard exists (Day 4), this script also starts
# it, polls /actuator/health until UP, and prints its URL - nothing to
# do yet, since today's lesson has no observable app-level behavior.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

echo "==> [1/5] Checking prerequisites"
command -v mvn >/dev/null 2>&1 || { echo "Maven not found - install Maven 3.9+"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java not found - install Java 17+"; exit 1; }

shopt -s nullglob
compose_dirs=()
for compose_file in */docker-compose.yml; do
  dir="$(dirname "$compose_file")"
  # Modules with a Dockerfile need a jar built first and get their own
  # explicit demo block in a later lesson - not handled generically here.
  if [ ! -f "${dir}/Dockerfile" ]; then
    compose_dirs+=("$dir")
  fi
done
shopt -u nullglob

if [ "${#compose_dirs[@]}" -gt 0 ]; then
  command -v docker >/dev/null 2>&1 || { echo "Docker not found - install Docker Desktop/Engine"; exit 1; }
fi

echo "==> [2/5] Resolving Maven dependencies"
mvn -q -pl streamsocial-common -am dependency:go-offline

echo "==> [3/5] Building and running unit + integration tests"
mvn -q -pl streamsocial-common -am verify

if [ "${#compose_dirs[@]}" -gt 0 ]; then
  echo "==> [4/5] Starting Docker Compose module(s): ${compose_dirs[*]}"
  for dir in "${compose_dirs[@]}"; do
    if [ -x "${dir}/scripts/start.sh" ]; then
      (cd "${dir}" && ./scripts/start.sh)
    else
      docker compose -f "$(pwd)/${dir}/docker-compose.yml" up -d
    fi
  done
else
  echo "==> [4/5] No Docker Compose module in this snapshot yet - skipping"
fi

echo "==> [5/5] Running today's demo(s)"
(cd streamsocial-common && mvn -q exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo)

if [ -x streamsocial-infra/scripts/verify-cluster.sh ]; then
  (cd streamsocial-infra && ./scripts/verify-cluster.sh)
fi

echo
echo "==> StreamSocial is up. Run ./stop.sh to tear everything down."
