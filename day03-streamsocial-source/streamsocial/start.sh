#!/usr/bin/env bash
# StreamSocial - one-command bring-up for this repository snapshot.
#
# Resolves Maven dependencies, builds, runs unit + integration tests,
# starts any Docker Compose-based module found in this snapshot (the
# Kafka cluster today, Connect/ksqlDB/etc. in later lessons - discovered
# automatically so this script doesn't need hand-editing every lesson),
# provisions anything that needs provisioning, and runs each lesson's
# demo end to end.
#
# Safe to re-run: already-healthy pieces are detected and left alone
# rather than restarted from scratch.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> [1/5] Checking prerequisites"
command -v mvn >/dev/null 2>&1 || { echo "Maven not found - install Maven 3.9+"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java not found - install Java 17+"; exit 1; }

shopt -s nullglob
compose_dirs=()
for compose_file in */docker-compose.yml; do
  compose_dirs+=("$(dirname "$compose_file")")
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
      (cd "${dir}" && docker compose up -d)
    fi
  done
else
  echo "==> [4/5] No Docker Compose module in this snapshot yet - skipping"
fi

echo "==> [5/5] Running today's demo(s)"
# Run exec:java only on streamsocial-common (no -am): -am includes the
# parent POM, which has no classpath for these demo main classes.
mvn -q -pl streamsocial-common exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo

if [ -f streamsocial-common/src/main/java/com/streamsocial/common/demo/TopicBootstrapDemo.java ]; then
  mvn -q -pl streamsocial-common exec:java \
    -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
  if [ -x streamsocial-infra/scripts/list-topics.sh ]; then
    (cd streamsocial-infra && ./scripts/list-topics.sh)
  fi
fi

echo
echo "==> StreamSocial is up. Run ./stop.sh to tear everything down."
