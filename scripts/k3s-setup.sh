#!/usr/bin/env bash
# =============================================================================
# scripts/k3s-setup.sh — One-time k3s + ArgoCD setup on Cyberlearnix VPS
#
# Run ONCE via SSH. After this, every git push triggers ArgoCD to auto-deploy.
#
# Prerequisites (export before running):
#   GHCR_TOKEN      — GitHub PAT with read:packages + write:packages
#   GHCR_USERNAME   — GitHub org/user owning the ghcr.io packages (Cyberlearnix)
#   DB_PASSWORD     — Postgres password
#   JWT_SECRET      — JWT signing secret
#   GITHUB_REPO     — e.g. "Cyberlearnix/cyberlearnix_website-dev"
#   REDIS_PASSWORD  — (optional) Redis password
#
# Usage from local Mac:
#   export GHCR_TOKEN=... GHCR_USERNAME=Cyberlearnix DB_PASSWORD=... JWT_SECRET=... GITHUB_REPO=Cyberlearnix/cyberlearnix_website-dev
#   ssh root@145.223.22.177 "$(declare -p GHCR_TOKEN GHCR_USERNAME DB_PASSWORD JWT_SECRET GITHUB_REPO); bash -s" < scripts/k3s-setup.sh
# =============================================================================
set -euo pipefail

# ── Validation ────────────────────────────────────────────────────────────────
: "${GHCR_TOKEN:?Set GHCR_TOKEN}"
: "${GHCR_USERNAME:?Set GHCR_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${JWT_SECRET:?Set JWT_SECRET}"
: "${GITHUB_REPO:?Set GITHUB_REPO (e.g. Cyberlearnix/cyberlearnix_website-dev)}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

log() { echo -e "\n\033[1;34m==> $*\033[0m"; }
ok()  { echo -e "\033[1;32m    ✓ $*\033[0m"; }
err() { echo -e "\033[1;31m    ✗ $*\033[0m" >&2; exit 1; }

# ── 0. Stop Docker Compose (frees ports 80, 443, 5432, 6379) ─────────────────
log "Stopping Docker Compose stack..."
if [[ -d "$HOME/cyberlearnix" ]] && command -v docker &>/dev/null; then
  cd "$HOME/cyberlearnix"
  docker compose down --remove-orphans 2>/dev/null || true
  ok "Docker Compose stopped"
else
  ok "Docker Compose not running — skipping"
fi

# ── 1. Install k3s ────────────────────────────────────────────────────────────
log "Installing k3s (lightweight Kubernetes)..."
if command -v k3s &>/dev/null; then
  ok "k3s already installed: $(k3s --version | head -1)"
else
  curl -sfL https://get.k3s.io | \
    INSTALL_K3S_EXEC="--write-kubeconfig-mode 644 --disable traefik" \
    sh -s - server
  # Disable built-in Traefik — nginx ingress controller will be used instead
  ok "k3s installed successfully"
fi

# Wait for k3s API server to be ready
log "Waiting for k3s API server..."
for i in $(seq 1 30); do
  if kubectl get nodes &>/dev/null 2>&1; then
    ok "k3s API server is ready"
    break
  fi
  echo "  waiting... ($i/30)"
  sleep 5
done
kubectl get nodes

# ── 2. Configure kubeconfig ───────────────────────────────────────────────────
log "Configuring kubeconfig..."
mkdir -p "$HOME/.kube"
cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"
export KUBECONFIG="$HOME/.kube/config"
grep -q "KUBECONFIG" "$HOME/.bashrc" 2>/dev/null || \
  echo 'export KUBECONFIG="$HOME/.kube/config"' >> "$HOME/.bashrc"
ok "kubeconfig saved to ~/.kube/config"

# ── 3. Install Helm 3 ─────────────────────────────────────────────────────────
log "Installing Helm 3..."
if command -v helm &>/dev/null; then
  ok "Helm already installed: $(helm version --short)"
else
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  ok "Helm installed: $(helm version --short)"
fi

# ── 4. Install NGINX Ingress Controller ───────────────────────────────────────
log "Installing NGINX Ingress Controller..."
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.hostNetwork=true \
  --set controller.kind=DaemonSet \
  --set controller.service.type=ClusterIP \
  --wait --timeout 3m
ok "NGINX Ingress Controller installed (hostNetwork mode — binds to port 80/443 on VPS)"

# ── 5. Install cert-manager ───────────────────────────────────────────────────
log "Installing cert-manager (for Let's Encrypt TLS certs)..."
helm repo add jetstack https://charts.jetstack.io --force-update
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set crds.enabled=true \
  --wait --timeout 3m
ok "cert-manager installed"

log "Applying Let's Encrypt ClusterIssuers..."
kubectl apply -f "$REPO_DIR/k8s/cluster-issuer.yaml"
# Wait for ClusterIssuer to become ready
for i in $(seq 1 12); do
  STAGING_READY=$(kubectl get clusterissuer letsencrypt-staging -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
  PROD_READY=$(kubectl get clusterissuer letsencrypt-prod -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
  if [[ "$STAGING_READY" == "True" && "$PROD_READY" == "True" ]]; then
    ok "ClusterIssuers are ready"
    break
  fi
  echo "  waiting for ClusterIssuers... ($i/12)"
  sleep 5
done
ok "cert-manager + ClusterIssuers configured"

# ── 6. Install ArgoCD ─────────────────────────────────────────────────────────
log "Installing ArgoCD..."
kubectl create namespace argocd 2>/dev/null || true
kubectl apply -n argocd --server-side \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

log "Waiting for ArgoCD to be ready..."
kubectl wait --for=condition=available deployment/argocd-server \
  -n argocd --timeout=3m
ok "ArgoCD installed"

# Patch ArgoCD server to use insecure mode (nginx handles TLS externally)
kubectl patch deployment argocd-server -n argocd \
  --type='json' \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--insecure"}]'

# ── 6. Clone repo ──────────────────────────────────────────────────────────────
log "Setting up repo at ~/cyberlearnix..."
REPO_DIR="$HOME/cyberlearnix"
if [[ -d "$REPO_DIR/.git" ]]; then
  git -C "$REPO_DIR" pull --ff-only
  ok "Repo updated"
else
  git clone "https://github.com/${GITHUB_REPO}.git" "$REPO_DIR"
  ok "Repo cloned to $REPO_DIR"
fi

# ── 7. Create namespaces ──────────────────────────────────────────────────────
log "Creating 'cyberlearnix' namespace..."
kubectl create namespace cyberlearnix 2>/dev/null || ok "Namespace already exists"
kubectl label namespace cyberlearnix \
  pod-security.kubernetes.io/enforce=baseline \
  pod-security.kubernetes.io/warn=restricted \
  --overwrite
ok "Namespace 'cyberlearnix' configured"

# ── 8. Create ghcr.io pull secret ─────────────────────────────────────────────
log "Creating ghcr.io pull secret..."
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username="$GHCR_USERNAME" \
  --docker-password="$GHCR_TOKEN" \
  --namespace cyberlearnix \
  --dry-run=client -o yaml | kubectl apply -f -
ok "ghcr.io pull secret created"

# ── 9. Create application secrets ────────────────────────────────────────────
log "Creating application secrets..."
kubectl create secret generic cyberlearnix-secrets \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --from-literal=redis-password="$REDIS_PASSWORD" \
  --namespace cyberlearnix \
  --dry-run=client -o yaml | kubectl apply -f -
ok "Application secrets created"

# ── 10. Apply ArgoCD Application manifest ─────────────────────────────────────
log "Registering Cyberlearnix app in ArgoCD..."
kubectl apply -f "$REPO_DIR/k8s/argocd-app.yaml"
ok "ArgoCD Application registered — will auto-sync from GitHub"

# ── 11. Get ArgoCD admin password ─────────────────────────────────────────────
log "ArgoCD admin password:"
ARGOCD_PASS=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)
echo "  Username: admin"
echo "  Password: $ARGOCD_PASS"
echo "  (save this — the secret will be deleted once you log in and change it)"

# ── 12. Status ────────────────────────────────────────────────────────────────
log "Status:"
echo ""
echo "k3s nodes:"
kubectl get nodes
echo ""
echo "ArgoCD:"
kubectl get pods -n argocd
echo ""
echo "App sync status (may still be syncing):"
kubectl get application -n argocd 2>/dev/null || true

VPS_IP=$(curl -s https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')

echo ""
echo "============================================================"
echo "  k3s + ArgoCD setup complete!"
echo ""
echo "  ArgoCD UI: http://$VPS_IP/argocd"
echo "  Username: admin  |  Password: $ARGOCD_PASS"
echo ""
echo "  From now on: every git push to main triggers ArgoCD"
echo "  to automatically deploy your updated services."
echo "============================================================"

