#!/usr/bin/env bash
# =============================================================================
# scripts/fix-ssl.sh — Install cert-manager + Let's Encrypt on a RUNNING cluster
#
# Run this via SSH on the VPS when the cluster is already up but SSL is broken:
#   ssh root@<VPS_IP> "bash -s" < scripts/fix-ssl.sh
#
# What it does:
#   1. Installs cert-manager via Helm (idempotent — safe to run again)
#   2. Applies the letsencrypt-staging + letsencrypt-prod ClusterIssuers
#   3. Deletes any stale/self-signed TLS secrets so cert-manager re-issues them
#   4. Shows certificate status
# =============================================================================
set -euo pipefail

log() { echo -e "\n\033[1;34m==> $*\033[0m"; }
ok()  { echo -e "\033[1;32m    ✓ $*\033[0m"; }
err() { echo -e "\033[1;31m    ✗ $*\033[0m" >&2; exit 1; }

REPO_DIR="${1:-$HOME/cyberlearnix}"

# ── 1. Pre-flight ─────────────────────────────────────────────────────────────
command -v kubectl &>/dev/null || err "kubectl not found. Run k3s-setup.sh first."
command -v helm    &>/dev/null || err "helm not found. Run k3s-setup.sh first."

log "Checking NGINX Ingress Controller..."
kubectl get pods -n ingress-nginx --no-headers 2>/dev/null | grep -q "Running" \
  || err "NGINX Ingress Controller is not running. Run k3s-setup.sh first."
ok "NGINX Ingress is running"

# ── 2. Install / upgrade cert-manager ────────────────────────────────────────
log "Installing cert-manager..."
helm repo add jetstack https://charts.jetstack.io --force-update
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set crds.enabled=true \
  --wait --timeout 5m
ok "cert-manager installed/upgraded"

# ── 3. Apply ClusterIssuers ───────────────────────────────────────────────────
log "Applying ClusterIssuers from repo..."
if [[ -f "$REPO_DIR/k8s/cluster-issuer.yaml" ]]; then
  kubectl apply -f "$REPO_DIR/k8s/cluster-issuer.yaml"
else
  # Inline fallback if repo not present
  log "Repo not found at $REPO_DIR — applying inline ClusterIssuers..."
  kubectl apply -f - <<'EOF'
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory
    email: admin@cyberlearnix.com
    privateKeySecretRef:
      name: letsencrypt-staging-key
    solvers:
      - http01:
          ingress:
            ingressClassName: nginx
---
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@cyberlearnix.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            ingressClassName: nginx
EOF
fi

log "Waiting for ClusterIssuers to become ready..."
for i in $(seq 1 18); do
  STAGING=$(kubectl get clusterissuer letsencrypt-staging \
    -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
  PROD=$(kubectl get clusterissuer letsencrypt-prod \
    -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
  if [[ "$STAGING" == "True" && "$PROD" == "True" ]]; then
    ok "Both ClusterIssuers are Ready"
    break
  fi
  echo "  waiting... ($i/18) staging=$STAGING prod=$PROD"
  sleep 5
done

# ── 4. Delete stale TLS secrets so cert-manager re-issues them ───────────────
log "Deleting stale TLS secrets in cyberlearnix namespace..."
for secret in $(kubectl get secrets -n cyberlearnix \
    -o jsonpath='{range .items[?(@.type=="kubernetes.io/tls")]}{.metadata.name}{"\n"}{end}' 2>/dev/null); do
  kubectl delete secret "$secret" -n cyberlearnix --ignore-not-found
  ok "Deleted stale secret: $secret"
done

# ── 5. Annotate existing ingresses to trigger cert re-issue ──────────────────
log "Touching ingress annotations to trigger certificate re-issue..."
for ing in $(kubectl get ingress -n cyberlearnix -o jsonpath='{.items[*].metadata.name}' 2>/dev/null); do
  kubectl annotate ingress "$ing" -n cyberlearnix \
    "cert-manager.io/issue-temporary-certificate=true" --overwrite
  kubectl annotate ingress "$ing" -n cyberlearnix \
    "cert-manager.io/issue-temporary-certificate-" --overwrite 2>/dev/null || true
  ok "Touched ingress: $ing"
done

# ── 6. Status ─────────────────────────────────────────────────────────────────
log "Certificate status:"
kubectl get certificate -n cyberlearnix 2>/dev/null || echo "  (no certificates yet — they will appear in ~30s)"
echo ""
log "ClusterIssuer status:"
kubectl get clusterissuer
echo ""
log "cert-manager pods:"
kubectl get pods -n cert-manager

echo ""
echo "================================================================"
echo "  SSL fix applied!"
echo ""
echo "  Let's Encrypt will now issue certificates for:"
kubectl get ingress -n cyberlearnix \
  -o jsonpath='{range .items[*]}{range .spec.tls[*]}{.hosts[*]}{"\n"}{end}{end}' 2>/dev/null \
  | sort -u | sed 's/^/    /'
echo ""
echo "  Certs are usually issued within 60-90 seconds."
echo "  Watch progress:  kubectl get certificate -n cyberlearnix -w"
echo "================================================================"
