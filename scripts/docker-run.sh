#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env.properties ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.properties
  set +a
fi

IMAGE_NAME="${IMAGE_NAME:-ecommerce-mcp-server:local}"
CONTAINER_NAME="${CONTAINER_NAME:-ecommerce-mcp-server}"
APP_PORT="${APP_PORT:-8080}"
MYSQL_HOST="${MYSQL_HOST:-host.docker.internal}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ecommerce_db}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME is required. Set it in .env.properties or the environment.}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required. Set it in .env.properties or the environment.}"
APP_SERVICE_TOKEN="${APP_SERVICE_TOKEN:?APP_SERVICE_TOKEN is required. Set it in .env.properties or the environment.}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}"

docker build -t "$IMAGE_NAME" .

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

docker run --rm \
  --name "$CONTAINER_NAME" \
  --add-host=host.docker.internal:host-gateway \
  -p "${APP_PORT}:8080" \
  -e SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -e APP_SERVICE_TOKEN="$APP_SERVICE_TOKEN" \
  "$IMAGE_NAME"
