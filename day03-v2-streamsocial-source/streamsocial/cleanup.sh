#!/usr/bin/env bash
# StreamSocial - stop project containers and reclaim unused Docker resources.
#
# Default: stop this project's Compose stack (with volumes), remove Maven
# target/, then prune unused Docker networks/images/build cache.
# Pass --all to also prune unused volumes and stop the Docker daemon.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

STOP_DOCKER_DAEMON=false
if [[ "${1:-}" == "--all" ]]; then
  STOP_DOCKER_DAEMON=true
fi

echo "==> [1/4] Stopping StreamSocial Compose stack (volumes included)"
if [[ -x ./stop.sh ]]; then
  ./stop.sh --wipe
else
  echo "    stop.sh not found - attempting compose down directly"
  if [[ -f streamsocial-infra/docker-compose.yml ]]; then
    docker compose -f streamsocial-infra/docker-compose.yml down -v --remove-orphans || true
  fi
fi

echo "==> [2/4] Removing Maven target/ directories"
find . -type d -name target -prune -exec rm -rf {} + 2>/dev/null || true
echo "    target/ cleaned"

echo "==> [3/4] Pruning unused Docker resources"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  # Remove any leftover containers named for this project
  leftover="$(docker ps -aq --filter 'name=streamsocial' 2>/dev/null || true)"
  if [[ -n "${leftover}" ]]; then
    echo "    Removing leftover streamsocial containers..."
    # shellcheck disable=SC2086
    docker rm -f ${leftover} >/dev/null || true
  fi

  docker network prune -f >/dev/null || true
  docker image prune -f >/dev/null || true
  docker builder prune -f >/dev/null || true

  if [[ "${STOP_DOCKER_DAEMON}" == "true" ]]; then
    echo "    Also pruning unused volumes (--all)..."
    docker volume prune -f >/dev/null || true
  fi
  echo "    Docker prune complete"
else
  echo "    Docker daemon not reachable - skipping prune"
fi

echo "==> [4/4] Docker daemon"
if [[ "${STOP_DOCKER_DAEMON}" == "true" ]]; then
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "    Docker already stopped"
  else
    echo "    Stopping Docker daemon..."
    stopped=false
    # Docker Desktop CLI (Windows/macOS host, or WSL integration)
    if docker desktop stop >/dev/null 2>&1; then
      stopped=true
    fi
    # Engine via systemd / SysV (needs passwordless or interactive sudo)
    if [[ "${stopped}" != "true" ]] && command -v systemctl >/dev/null 2>&1; then
      if sudo -n systemctl stop docker.socket docker >/dev/null 2>&1 \
        || sudo -n systemctl stop docker >/dev/null 2>&1; then
        stopped=true
      fi
    fi
    if [[ "${stopped}" != "true" ]] && command -v service >/dev/null 2>&1; then
      if sudo -n service docker stop >/dev/null 2>&1; then
        stopped=true
      fi
    fi
    sleep 1
    if docker info >/dev/null 2>&1; then
      echo "    Could not stop the Docker daemon automatically (sudo password required)."
      echo "    Stop it manually, e.g.:"
      echo "      sudo systemctl stop docker.socket docker"
      echo "      # or on Docker Desktop: quit the app / 'docker desktop stop'"
    else
      echo "    Docker daemon stopped"
    fi
  fi
else
  echo "    Daemon left running (pass --all to stop it too)"
fi

echo
echo "==> Cleanup finished."
echo "    Re-start later with: ./start.sh"
echo "    Full cleanup (incl. Docker daemon): ./cleanup.sh --all"
