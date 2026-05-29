#!/bin/bash
set -e

SERVICES="gateway-service notification-service form-service cms-service enrollment-service course-service instructor-service user-service admin-service shop-service"

echo "=== Importing all service images into K3s containerd ==="
for svc in $SERVICES; do
  echo "--- Importing $svc ---"
  docker save cyberlearnix/cyberlearnix-$svc:latest | sudo k3s ctr images import -
  echo "    Done: cyberlearnix/cyberlearnix-$svc"
done

echo ""
echo "=== Images in K3s containerd ==="
sudo k3s ctr images ls | grep cyberlearnix
