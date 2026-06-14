#!/bin/bash
SVCS="user-service gateway-service admin-service attendance-service cms-service course-service enrollment-service form-service instructor-service notification-service shop-service"
for svc in $SVCS; do
  echo "Fixing pullPolicy for $svc in dev..."
  kubectl patch deployment $svc -n cyberlearnix --type=json \
    -p '[{"op":"replace","path":"/spec/template/spec/containers/0/imagePullPolicy","value":"IfNotPresent"}]' 2>&1
  
  echo "Fixing pullPolicy for $svc in prod..."
  kubectl patch deployment $svc -n cyberlearnix-production --type=json \
    -p '[{"op":"replace","path":"/spec/template/spec/containers/0/imagePullPolicy","value":"IfNotPresent"}]' 2>&1
done
echo "ALL_PULL_POLICIES_FIXED"
