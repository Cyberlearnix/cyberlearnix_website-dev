#!/bin/bash
set -e

export KUBECONFIG=/home/swachvegadev/.kube/config

echo "=== Updating Docker Hub pull secret ==="
kubectl create secret docker-registry dockerhub-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=swachvegadev \
  --docker-password='dckr_pat_vMkrievoBe_JB2MgW5Hp4ZaCVg4' \
  --namespace cyberlearnix --dry-run=client -o yaml | kubectl apply -f -

echo "=== Getting VM private IP ==="
HOST_IP=$(ip route get 1.1.1.1 | awk 'NR==1{print $7}')
echo "VM private IP: $HOST_IP"

echo "=== Running Helm deploy (no-wait) ==="
helm upgrade --install cyberlearnix ~/cyberlearnix/helm \
  -f ~/cyberlearnix/helm/values-k3s.yaml \
  --set "appConfig.DB_HOST=$HOST_IP" \
  --set "appConfig.REDIS_HOST=$HOST_IP" \
  --namespace cyberlearnix \
  --create-namespace \
  --timeout 5m

echo "=== Pod status ==="
kubectl get pods -n cyberlearnix

echo ""
echo "=== Events for any pending/errored pods ==="
kubectl get events -n cyberlearnix --sort-by='.lastTimestamp' | tail -30
