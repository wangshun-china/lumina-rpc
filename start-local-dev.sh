#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
  ROOT_DIR="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/../docker-compose.yml" ]; then
  ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
else
  echo "ERROR: docker-compose.yml not found near $SCRIPT_DIR" >&2
  exit 1
fi

CONTAINERS="
lumina-mysql
lumina-control-plane
lumina-dashboard
lumina-sample-engine
lumina-sample-signal
lumina-sample-radar
lumina-sample-command
"

echo "==> Stop deployed lumina containers if they exist"
for name in $CONTAINERS; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    docker stop "$name" >/dev/null 2>&1 || true
    docker rm -f "$name" >/dev/null 2>&1 || true
    echo "removed: $name"
  fi
done

echo "==> Start local MySQL only"
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d mysql

echo "==> Wait for local MySQL healthcheck"
for i in $(seq 1 30); do
  status=$(docker inspect -f '{{.State.Health.Status}}' lumina-mysql 2>/dev/null || echo "missing")
  if [ "$status" = "healthy" ]; then
    echo "mysql healthy"
    break
  fi

  if [ "$i" -eq 30 ]; then
    echo "ERROR: lumina-mysql is not healthy after waiting" >&2
    docker ps -a --filter "name=lumina-mysql" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    docker logs lumina-mysql --tail 80 2>/dev/null || true
    exit 1
  fi

  echo "waiting mysql... ($i/30, status=$status)"
  sleep 2
done

echo "==> Running containers"
docker ps --filter "name=lumina" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

echo "==> Local MySQL is up. Data volume is preserved."
