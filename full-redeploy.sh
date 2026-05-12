#!/bin/bash
# full-redeploy.sh — kills all pods, ensures DBs exist, redeploys from scratch
# Run this when rolling update is stuck due to Postgres max_connections overflow.
set -e
export KUBECONFIG=/home/swachvegadev/.kube/config

echo "=== Step 0: Copy updated Helm files to chart directory ==="
# Files are SCP'd to ~ (home dir), need to move them into the chart
[ -f ~/deployment.yaml ] && cp ~/deployment.yaml ~/cyberlearnix/helm/templates/deployment.yaml && echo "  deployment.yaml updated"
[ -f ~/values-k3s.yaml ] && cp ~/values-k3s.yaml ~/cyberlearnix/helm/values-k3s.yaml && echo "  values-k3s.yaml updated"

echo "=== Step 1: Scale all deployments to 0 (free Postgres connections) ==="
kubectl scale deployment --all --replicas=0 -n cyberlearnix

echo "=== Waiting for all pods to terminate ==="
# Wait up to 120s for all pods to go away
for i in $(seq 1 24); do
  RUNNING=$(kubectl get pods -n cyberlearnix --no-headers 2>/dev/null | grep -c Running || true)
  PENDING=$(kubectl get pods -n cyberlearnix --no-headers 2>/dev/null | grep -c Pending || true)
  TOTAL=$((RUNNING + PENDING))
  echo "  Pods still running/pending: $TOTAL (check $i/24)"
  if [ "$TOTAL" -eq 0 ]; then
    echo "  All pods terminated."
    break
  fi
  sleep 5
done

echo ""
echo "=== Step 2: Ensure all databases exist ==="
bash ~/create-all-dbs.sh

echo ""
echo "=== Step 3: Verify databases ==="
bash ~/check-dbs.sh

echo ""
echo "=== Step 4: Deploy with updated Helm chart ==="
HOST_IP=$(ip route get 1.1.1.1 | awk 'NR==1{print $7}')
helm upgrade --install cyberlearnix ~/cyberlearnix/helm \
  -f ~/cyberlearnix/helm/values-k3s.yaml \
  --set "appConfig.DB_HOST=$HOST_IP" \
  --set "appConfig.REDIS_HOST=$HOST_IP" \
  --namespace cyberlearnix --create-namespace --timeout 5m

echo ""
echo "=== Deployment triggered. Monitor with: ==="
echo "    kubectl get pods -n cyberlearnix -w"
echo ""
echo "=== Current pod status ==="
kubectl get pods -n cyberlearnix
