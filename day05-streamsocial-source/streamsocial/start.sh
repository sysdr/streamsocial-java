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
mvn -q -pl streamsocial-common,streamsocial-producer-service,streamsocial-engagement-consumer -am verify

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
# Install modules so module-scoped exec:java can resolve reactor artifacts,
# then target each module POM directly (reactor -pl runs exec on the parent).
mvn -q -pl streamsocial-common,streamsocial-producer-service,streamsocial-engagement-consumer -am install -DskipTests

mvn -q -f streamsocial-common/pom.xml exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo

if [ -f streamsocial-common/src/main/java/com/streamsocial/common/demo/TopicBootstrapDemo.java ]; then
  mvn -q -f streamsocial-common/pom.xml exec:java \
    -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo
  if [ -x streamsocial-infra/scripts/list-topics.sh ]; then
    (cd streamsocial-infra && ./scripts/list-topics.sh) || true
  fi
fi

if [ -d streamsocial-producer-service ]; then
  echo "==> Building and starting streamsocial-producer-service"
  mvn -q -pl streamsocial-producer-service -am package -DskipTests
  java -jar streamsocial-producer-service/target/streamsocial-producer-service.jar > /tmp/streamsocial-producer-service.log 2>&1 &
  PRODUCER_PID=$!

  echo "    waiting for the service to accept requests..."
  ready=false
  for _ in $(seq 1 45); do
    if grep -q "Started StreamSocialProducerServiceApplication" /tmp/streamsocial-producer-service.log 2>/dev/null; then
      ready=true
      break
    fi
    sleep 1
  done

  if [ "${ready}" = true ]; then
    echo "    posting a sample user action..."
    curl -s -X POST http://localhost:8081/api/v1/actions \
      -H 'Content-Type: application/json' \
      -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
    echo
    echo "    (raw KafkaProducer demo: mvn -pl streamsocial-producer-service -am exec:java -Dexec.mainClass=com.streamsocial.producer.demo.RawProducerDemo)"
  else
    echo "    service did not become ready in time - check /tmp/streamsocial-producer-service.log"
  fi

  echo "    stopping streamsocial-producer-service (background demo instance)"
  kill "${PRODUCER_PID}" 2>/dev/null || true
fi

if [ -d streamsocial-engagement-consumer ]; then
  echo "==> Building and starting streamsocial-engagement-consumer"
  mvn -q -pl streamsocial-engagement-consumer -am package -DskipTests
  java -jar streamsocial-engagement-consumer/target/streamsocial-engagement-consumer.jar \
    > /tmp/streamsocial-engagement-consumer.log 2>&1 &
  CONSUMER_PID=$!
  for _ in $(seq 1 45); do
    if grep -q "partitions assigned" /tmp/streamsocial-engagement-consumer.log 2>/dev/null; then
      break
    fi
    sleep 1
  done

  echo "    publishing test engagement traffic (including one deliberate failure)..."
  mvn -q -f streamsocial-engagement-consumer/pom.xml exec:java \
    -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator

  echo "    waiting for processing and error-recovery log lines..."
  sleep 8
  echo "    --- streamsocial-engagement-consumer log (STRUCTURED_ lines) ---"
  grep "STRUCTURED_" /tmp/streamsocial-engagement-consumer.log || echo "    (no STRUCTURED_ lines yet - check /tmp/streamsocial-engagement-consumer.log)"

  echo "    stopping streamsocial-engagement-consumer (background demo instance)"
  kill "${CONSUMER_PID}" 2>/dev/null || true
fi

echo
echo "==> StreamSocial is up. Run ./stop.sh to tear everything down."
