#!/bin/bash
set -e

SERVICES="gateway-service notification-service form-service cms-service enrollment-service course-service instructor-service user-service admin-service shop-service"

echo "=== Logging in to Docker Hub ==="
echo "dckr_pat_vMkrievoBe_JB2MgW5Hp4ZaCVg4" | docker login -u swachvegadev --password-stdin

echo "=== Tagging and pushing images ==="
for svc in $SERVICES; do
  echo "--- $svc ---"
  docker tag cyberlearnix/cyberlearnix-$svc:latest swachvegadev/cyberlearnix-$svc:latest
  docker push swachvegadev/cyberlearnix-$svc:latest
done

echo "=== All images pushed ==="
