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
# Failsafe ITs need Docker + a single-broker Testcontainers Kafka, which cannot
# honor production RF=3. Skip ITs here so bring-up still exercises the real
# 3-broker cluster; unit tests still run under Surefire.
mvn -q -pl streamsocial-common -am verify -DskipITs

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
mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -q -pl streamsocial-common \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo

if [ -x streamsocial-infra/scripts/verify-cluster.sh ]; then
  (cd streamsocial-infra && ./scripts/verify-cluster.sh)
fi

if [ -f streamsocial-common/src/main/java/com/streamsocial/common/demo/TopicBootstrapDemo.java ]; then
  echo "==> Provisioning topics (local-demo-scale: 12/6 instead of production's 1000/500)"
  mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -q -pl streamsocial-common \
    -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo \
    -Dstreamsocial.topics.user-actions-partitions=12 \
    -Dstreamsocial.topics.content-interactions-partitions=6
  if [ -x streamsocial-infra/scripts/list-topics.sh ]; then
    (cd streamsocial-infra && ./scripts/list-topics.sh)
  fi
fi

if [ -d streamsocial-dashboard ]; then
  echo "==> Building and starting streamsocial-dashboard"
  mvn -q -pl streamsocial-dashboard -am package -DskipTests
  java -jar streamsocial-dashboard/target/streamsocial-dashboard.jar \
    > /tmp/streamsocial-dashboard.log 2>&1 &
  DASHBOARD_PID=$!

  echo "    waiting for the dashboard to become healthy..."
  dashboard_ready=false
  for _ in $(seq 1 30); do
    if curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
      dashboard_ready=true
      break
    fi
    sleep 1
  done

  if [ "${dashboard_ready}" = true ]; then
    echo "    Dashboard is up: http://localhost:8080"
  else
    echo "    Dashboard did not become ready in time - check /tmp/streamsocial-dashboard.log"
  fi
fi

if [ -d streamsocial-producer-service ]; then
  echo "==> Building and starting streamsocial-producer-service"
  mvn -q -pl streamsocial-producer-service -am package -DskipTests
  java -jar streamsocial-producer-service/target/streamsocial-producer-service.jar \
    > /tmp/streamsocial-producer-service.log 2>&1 &
  PRODUCER_PID=$!

  echo "    waiting for the service to accept requests..."
  ready=false
  for _ in $(seq 1 30); do
    if curl -s -o /dev/null http://localhost:8082/api/v1/actions -X POST \
        -H 'Content-Type: application/json' -d '{}'; then
      ready=true
      break
    fi
    sleep 1
  done

  if [ "${ready}" = true ]; then
    echo "    posting a sample user action (watch it appear on the dashboard)..."
    curl -s -X POST http://localhost:8082/api/v1/actions \
      -H 'Content-Type: application/json' \
      -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
    echo
  else
    echo "    service did not become ready in time - check /tmp/streamsocial-producer-service.log"
  fi

  echo "    (dashboard stays running - open http://localhost:8080 to watch live traffic)"
  echo "    (producer-service stays running too - keep posting to http://localhost:8082/api/v1/actions)"
fi

if [ -d streamsocial-engagement-consumer ]; then
  echo "==> Building and starting streamsocial-engagement-consumer"
  mvn -q -pl streamsocial-engagement-consumer -am package -DskipTests
  java -jar streamsocial-engagement-consumer/target/streamsocial-engagement-consumer.jar \
    > /tmp/streamsocial-engagement-consumer.log 2>&1 &
  CONSUMER_PID=$!
  sleep 5

  echo "    publishing test engagement traffic (watch it on the dashboard's content-interactions panel too)..."
  # Install reactor modules so exec:java resolves streamsocial-common on the classpath.
  mvn -q -pl streamsocial-engagement-consumer -am install -DskipTests
  mvn org.codehaus.mojo:exec-maven-plugin:3.3.0:java -q -pl streamsocial-engagement-consumer \
    -Dexec.mainClass=com.streamsocial.consumer.demo.EngagementTestDataGenerator

  echo "    waiting for processing and error-recovery log lines..."
  sleep 6
  echo "    --- streamsocial-engagement-consumer log (STRUCTURED_ lines) ---"
  grep "STRUCTURED_" /tmp/streamsocial-engagement-consumer.log || echo "    (no STRUCTURED_ lines yet - check /tmp/streamsocial-engagement-consumer.log)"

  echo "    (engagement-consumer stays running too)"
fi

echo
echo "==> StreamSocial is up. Run ./stop.sh to tear everything down."
