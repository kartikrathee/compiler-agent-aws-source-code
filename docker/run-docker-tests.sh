#!/bin/bash
set -e

# 1. Start Atlas
echo "=== Starting Atlas with docker-compose ==="
docker compose up -d --build

# Wait for healthy
echo "Waiting for Atlas to be healthy..."
# Portable wait without 'timeout' command
MAX_RETRIES=60
COUNT=0
until curl -sf http://localhost:4566/_atlas/health >/dev/null 2>&1; do
  if [ $COUNT -ge $MAX_RETRIES ]; then
    echo "Atlas failed to become healthy in time"
    exit 1
  fi
  sleep 1
  COUNT=$((COUNT + 1))
  echo -n "."
done
echo " Atlas is up!"

# 2. Network setup (Atlas uses atlas_default from compose)
NETWORK="atlas_default"
DOCKER_GID=$(stat -c '%g' /var/run/docker.sock 2>/dev/null || stat -f '%g' /var/run/docker.sock)

# Atlas's embedded DNS server resolves *.atlas → Atlas's IP.
# Passing --dns <atlas-ip> to test containers lets the S3 virtual-host client
# send to <bucket>.atlas:4566 which Atlas DNS resolves correctly. Without this,
# Docker's built-in DNS only resolves the exact service name "atlas", not
# wildcard subdomains like my-bucket.atlas.
ATLAS_CONTAINER=$(docker compose ps -q atlas 2>/dev/null | head -1)
ATLAS_IP=$(docker inspect -f "{{.NetworkSettings.Networks.${NETWORK}.IPAddress}}" "$ATLAS_CONTAINER" 2>/dev/null || true)

# 3. Test suites
SUITES=(
  "sdk-test-python"
  "sdk-test-node"
  "sdk-test-java"
  "sdk-test-go"
  "sdk-test-awscli"
  "compat-cdk"
  "compat-terraform"
  "compat-opentofu"
)

# results dir
mkdir -p test-results

for suite in "${SUITES[@]}"; do
  echo "=== Running $suite in Docker ==="
  
  IMAGE_NAME="compat-$suite"
  
  # Build
  docker build -q -t "$IMAGE_NAME" "compatibility-tests/$suite"
  
  # Build DNS args: if we resolved Atlas's IP, inject it as the DNS server so
  # wildcard subdomains like <bucket>.atlas resolve inside test containers.
  DNS_ARGS=()
  if [ -n "$ATLAS_IP" ]; then
    DNS_ARGS=(--dns "$ATLAS_IP")
  fi

  # Run
  docker run --rm --network "$NETWORK" \
    "${DNS_ARGS[@]}" \
    -e ATLAS_ENDPOINT=http://atlas:4566 \
    -e ATLAS_S3_VHOST_ENDPOINT=http://atlas:4566 \
    -v "$(pwd)/test-results:/results" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --group-add "$DOCKER_GID" \
    "$IMAGE_NAME" || echo "Test suite $suite failed"
done

echo "=== All Docker tests completed ==="
