#!/usr/bin/env bash
# Start streamsocial-producer-service, freeing port 8081 first so a leftover
# instance from a previous run cannot fail the boot with "port already in use".
set -euo pipefail
cd "$(dirname "$0")"

if command -v fuser >/dev/null 2>&1; then
  if fuser 8081/tcp >/dev/null 2>&1; then
    echo "==> Port 8081 is in use - stopping the previous process"
    fuser -k 8081/tcp >/dev/null 2>&1 || true
    sleep 1
  fi
fi

echo "==> Starting streamsocial-producer-service on http://localhost:8081"
echo "    (Ctrl+C to stop)"
exec mvn -pl streamsocial-producer-service -am spring-boot:run
