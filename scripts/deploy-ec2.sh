#!/bin/bash
set -Eeuo pipefail

PROJECT_DIR="/home/ubuntu/CAP04-Food-Recall-Traceability"
COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env"
IMAGE_TAG="${1:-latest}"

if [[ ! "$IMAGE_TAG" =~ ^[a-f0-9]{40}$ && "$IMAGE_TAG" != "latest" ]]; then
    echo "ERROR: Invalid image tag."
    exit 2
fi

cd "$PROJECT_DIR"

echo "===== CAP04 DEPLOYMENT STARTED ====="
echo "Deploying image tag: $IMAGE_TAG"

sed -i '/^IMAGE_TAG=/d' "$ENV_FILE"
printf 'IMAGE_TAG=%s\n' "$IMAGE_TAG" >> "$ENV_FILE"

echo "Step 1: Pulling Docker images..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull

echo "Step 2: Starting containers..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans

echo "Step 3: Checking PostgreSQL..."
for i in $(seq 1 30); do
    STATUS=$(docker inspect -f '{{.State.Health.Status}}' cap04-postgres 2>/dev/null || true)
    if [ "$STATUS" = "healthy" ]; then
        echo "PostgreSQL is healthy."
        break
    fi
    if [ "$i" -eq 30 ]; then
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50 postgres
        exit 1
    fi
    sleep 5
done

echo "Step 4: Checking backend..."
for i in $(seq 1 30); do
    if curl -fsS http://localhost:8080/actuator/health >/dev/null; then
        echo "Backend is healthy."
        break
    fi
    if [ "$i" -eq 30 ]; then
        docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=80 backend
        exit 1
    fi
    sleep 5
done

echo "Step 5: Checking frontend..."
curl -fsS http://localhost:3000/ >/dev/null

echo "===== DEPLOYMENT SUCCESSFUL ====="
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
