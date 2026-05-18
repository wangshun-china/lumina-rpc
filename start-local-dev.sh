#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

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

echo "==> Running containers"
docker ps --filter "name=lumina" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

echo "==> Local MySQL is up. Data volume is preserved."
