#!/usr/bin/env bash
# =============================================================================
# scripts/k3s-setup.sh — One-time K3s setup on the Cyberlearnix VM
#
# Run this ONCE via SSH to set up K3s on your VM.
# After this, CI/CD (helm-deploy.yml) handles all subsequent deployments.
#
# Prerequisites (set as environment variables before running):
#   DOCKERHUB_USERNAME   — Docker Hub username
#   DOCKERHUB_TOKEN      — Docker Hub access token
#   DB_PASSWORD          — Postgres password (must match docker-compose.yml)
#   JWT_SECRET           — JWT signing secret (must match running services)
#   GITHUB_REPO          — e.g. "Cyberlearnix/cyberlearnix_website-dev"
#   REDIS_PASSWORD       — (optional) Redis password if AUTH is enabled
#
# Usage from your local machine (SSH):
#   ssh swachvegadev@20.197.21.226 "bash -s" < scripts/k3s-setup.sh
#
# Or copy + source on the VM:
#   scp scripts/k3s-setup.sh swachvegadev@20.197.21.226:~/
#   ssh swachvegadev@20.197.21.226
#   export DOCKERHUB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... GITHUB_REPO=...
#   bash k3s-setup.sh
# =============================================================================
set -euo pipefail

# ── Validation ────────────────────────────────────────────────────────────────
: "${DOCKERHUB_USERNAME:?Set DOCKERHUB_USERNAME}"
: "${DOCKERHUB_TOKEN:?Set DOCKERHUB_TOKEN}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${JWT_SECRET:?Set JWT_SECRET}"
: "${GITHUB_REPO:?Set GITHUB_REPO (e.g. Cyberlearnix/cyberlearnix_website-dev)}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

log() { echo -e "\n\033[1;34m==> $*\033[0m"; }
ok()  { echo -e "\033[1;32m    ✓ $*\033[0m"; }
err() { echo -e "\033[1;31m    ✗ $*\033[0m" >&2; exit 1; }

# ── 1. Install K3s ────────────────────────────────────────────────────────────
log "Installing K3s (lightweight Kubernetes)..."
if command -v k3s &>/dev/null; then
  ok "K3s already installed: $(k3s --version | head -1)"
else
  # --write-kubeconfig-mode 644 so helm/kubectl work as non-root
  curl -sfL https://get.k3s.io | \
    INSTALL_K3S_EXEC="--write-kubeconfig-mode 644" \
    sh -s - server
  ok "K3s installed successfully"
fi

# Wait for K3s API server to be ready
log "Waiting for K3s API server..."
for i in $(seq 1 30); do
  if kubectl get nodes &>/dev/null; then
    ok "K3s API server is ready"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 5
done
kubectl get nodes

# ── 2. Configure kubeconfig for current user ──────────────────────────────────
log "Configuring kubeconfig..."
mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"
export KUBECONFIG="$HOME/.kube/config"

# Persist in shell profile
if ! grep -q "KUBECONFIG" "$HOME/.bashrc" 2>/dev/null; then
  echo 'export KUBECONFIG="$HOME/.kube/config"' >> "$HOME/.bashrc"
fi
ok "kubeconfig saved to ~/.kube/config"

# ── 3. Install Helm 3 ─────────────────────────────────────────────────────────
log "Installing Helm 3..."
if command -v helm &>/dev/null; then
  ok "Helm already installed: $(helm version --short)"
else
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  ok "Helm installed: $(helm version --short)"
fi

# ── 4. Clone the repo on the VM ──────────────────────────────────────────────
log "Setting up repo at ~/cyberlearnix..."
REPO_DIR="$HOME/cyberlearnix"
if [[ -d "$REPO_DIR/.git" ]]; then
  ok "Repo already cloned — pulling latest..."
  git -C "$REPO_DIR" pull --ff-only
else
  git clone "https://github.com/${GITHUB_REPO}.git" "$REPO_DIR"
  ok "Repo cloned to $REPO_DIR"
fi

# ── 5. Detect VM's private IP (accessible from K3s pods) ─────────────────────
log "Detecting VM private IP for pod→Docker connectivity..."
HOST_IP=$(ip route get 1.1.1.1 2>/dev/null | awk 'NR==1{print $7}')
if [[ -z "$HOST_IP" ]]; then
  # Fallback: use the IP of the primary interface
  HOST_IP=$(hostname -I | awk '{print $1}')
fi
echo "  VM private IP: $HOST_IP"
ok "Pods will reach Postgres/Redis at $HOST_IP"

# ── 6. Create namespace ───────────────────────────────────────────────────────
log "Creating 'cyberlearnix' namespace..."
kubectl create namespace cyberlearnix 2>/dev/null || ok "Namespace already exists"

# Apply PSA labels (restrict pod security)
kubectl label namespace cyberlearnix \
  pod-security.kubernetes.io/enforce=baseline \
  pod-security.kubernetes.io/warn=restricted \
  --overwrite
ok "Namespace 'cyberlearnix' configured"

# NOTE: Using 'baseline' not 'restricted' because restricted requires
# seccompProfile: RuntimeDefault which K3s v1.24 default containerd may not
# support on all distros. Set to 'restricted' once you verify pods start cleanly.

# ── 7. Create Docker Hub image pull secret ────────────────────────────────────
log "Creating Docker Hub pull secret..."
kubectl create secret docker-registry dockerhub-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username="$DOCKERHUB_USERNAME" \
  --docker-password="$DOCKERHUB_TOKEN" \
  --namespace cyberlearnix \
  --dry-run=client -o yaml | kubectl apply -f -
ok "Docker Hub pull secret created/updated"

# ── 8. Create application secrets ────────────────────────────────────────────
log "Creating application secrets (DB passwords, JWT)..."
kubectl create secret generic cyberlearnix-secrets \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --from-literal=redis-password="$REDIS_PASSWORD" \
  --namespace cyberlearnix \
  --dry-run=client -o yaml | kubectl apply -f -
ok "Application secrets created/updated"

# ── 9. Initial Helm deploy ────────────────────────────────────────────────────
log "Running initial Helm deploy..."
cd "$REPO_DIR"

helm upgrade --install cyberlearnix ./helm \
  -f ./helm/values-k3s.yaml \
  --set global.registry="$DOCKERHUB_USERNAME" \
  --set appConfig.DB_HOST="$HOST_IP" \
  --set appConfig.REDIS_HOST="$HOST_IP" \
  --namespace cyberlearnix \
  --create-namespace \
  --timeout 5m \
  --wait

ok "Initial Helm deploy complete!"

# ── 10. Status summary ────────────────────────────────────────────────────────
log "Deployment status:"
kubectl get pods -n cyberlearnix
echo ""
kubectl get svc -n cyberlearnix
echo ""
kubectl get ingress -n cyberlearnix

echo ""
echo "============================================================"
echo "  K3s setup complete!"
echo ""
echo "  Gateway service: http://$HOST_IP:80 (via Traefik Ingress)"
echo "  Or directly:     http://$HOST_IP:8080"
echo ""
echo "  Verify: curl http://$HOST_IP/actuator/health"
echo ""
echo "  CI/CD: the helm-deploy.yml workflow will now deploy"
echo "         automatically on every push to main."
echo "============================================================"
