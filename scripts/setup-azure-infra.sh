#!/usr/bin/env bash
# =============================================================================
# Cyberlearnix — Azure Infrastructure Setup
# Run ONCE to provision all Azure resources before the first deployment.
# Pre-requisites: az CLI installed, logged in, subscription set.
#
# Usage:
#   chmod +x scripts/setup-azure-infra.sh
#   ./scripts/setup-azure-infra.sh
# =============================================================================
set -euo pipefail

# ── Configuration — edit these before running ────────────────────────────────
SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID:-}"       # set or export before running
RESOURCE_GROUP="cyberlearnix-rg"
LOCATION="eastus"
CLUSTER_NAME="cyberlearnix-aks"
ACR_NAME="swachvegaregistry"                       # already exists
POSTGRES_SERVER_NAME="cyberlearnix-pg-prod"
REDIS_CACHE_NAME="cyberlearnix-redis-prod"
KEY_VAULT_NAME="cyberlearnix-kv"
GITHUB_ORG="<YOUR_GITHUB_ORG>"                    # e.g. cyberlearnix
GITHUB_REPO="<YOUR_GITHUB_REPO>"                  # e.g. cyberlearnix_website-dev
GITHUB_APP_NAME="cyberlearnix-gh-oidc"

# AKS node config (right-size for your workload — 10 services)
AKS_NODE_COUNT=3
AKS_NODE_VM="Standard_D4s_v5"    # 4 vCPU, 16 GB RAM — runs ~4 services per node

# ── Validation ────────────────────────────────────────────────────────────────
if [[ -z "$SUBSCRIPTION_ID" ]]; then
  echo "❌ AZURE_SUBSCRIPTION_ID is not set. Export it before running."
  exit 1
fi

echo "════════════════════════════════════════════════════════════"
echo " Cyberlearnix — Azure Infrastructure Provisioning"
echo " Subscription : $SUBSCRIPTION_ID"
echo " Resource Group: $RESOURCE_GROUP ($LOCATION)"
echo "════════════════════════════════════════════════════════════"

# ── 1. Resource Group ─────────────────────────────────────────────────────────
echo ""
echo "▶ [1/9] Creating resource group..."
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags project=cyberlearnix environment=production managed-by=azure-cli
echo "✓ Resource group ready"

# ── 2. AKS Cluster ────────────────────────────────────────────────────────────
echo ""
echo "▶ [2/9] Creating AKS cluster (this takes ~5 minutes)..."
az aks create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CLUSTER_NAME" \
  --location "$LOCATION" \
  --node-count "$AKS_NODE_COUNT" \
  --node-vm-size "$AKS_NODE_VM" \
  --enable-managed-identity \
  --enable-oidc-issuer \
  --enable-workload-identity \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 8 \
  --network-plugin azure \
  --network-policy azure \
  --zones 1 2 3 \
  --kubernetes-version "1.29" \
  --os-sku AzureLinux \
  --auto-upgrade-channel patch \
  --generate-ssh-keys \
  --tags project=cyberlearnix managed-by=terraform
echo "✓ AKS cluster ready"

# ── 3. Attach ACR to AKS (no pull secret needed — uses managed identity) ──────
echo ""
echo "▶ [3/9] Attaching ACR to AKS..."
az aks update \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CLUSTER_NAME" \
  --attach-acr "$ACR_NAME"
echo "✓ AKS ↔ ACR attachment complete (kubelet can pull images without credentials)"

# ── 4. Azure Database for PostgreSQL Flexible Server ─────────────────────────
echo ""
echo "▶ [4/9] Creating PostgreSQL Flexible Server..."
PG_ADMIN_PASS=$(openssl rand -base64 32)
az postgres flexible-server create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$POSTGRES_SERVER_NAME" \
  --location "$LOCATION" \
  --admin-user pgadmin \
  --admin-password "$PG_ADMIN_PASS" \
  --sku-name Standard_D4s_v5 \
  --tier GeneralPurpose \
  --storage-size 64 \
  --version 15 \
  --high-availability ZoneRedundant \
  --zone 1 \
  --standby-zone 2 \
  --backup-retention 7 \
  --geo-redundant-backup Enabled

# Create databases for each service
DATABASES=(cyberlearnix_users cyberlearnix_courses cyberlearnix_enrollments \
           cyberlearnix_shop cyberlearnix_forms cyberlearnix_admin \
           cyberlearnix_cms cyberlearnix_instructor)
for DB in "${DATABASES[@]}"; do
  az postgres flexible-server db create \
    --resource-group "$RESOURCE_GROUP" \
    --server-name "$POSTGRES_SERVER_NAME" \
    --database-name "$DB"
  echo "  ✓ Database: $DB"
done

# Allow AKS subnet access
AKS_SUBNET_ID=$(az aks show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CLUSTER_NAME" \
  --query "agentPoolProfiles[0].vnetSubnetId" -o tsv 2>/dev/null || echo "")

if [[ -n "$AKS_SUBNET_ID" ]]; then
  az postgres flexible-server network add-vnet --help > /dev/null 2>&1 && \
  echo "  ℹ️ Configure PostgreSQL VNet integration in Azure Portal > $POSTGRES_SERVER_NAME > Networking"
fi
echo "✓ PostgreSQL ready — admin password saved below (store in Key Vault)"

# ── 5. Azure Cache for Redis ──────────────────────────────────────────────────
echo ""
echo "▶ [5/9] Creating Redis Cache..."
az redis create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$REDIS_CACHE_NAME" \
  --location "$LOCATION" \
  --sku Standard \
  --vm-size c1 \
  --minimum-tls-version 1.2 \
  --redis-version 7
echo "✓ Redis Cache ready"

# ── 6. Azure Key Vault ────────────────────────────────────────────────────────
echo ""
echo "▶ [6/9] Creating Key Vault..."
az keyvault create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$KEY_VAULT_NAME" \
  --location "$LOCATION" \
  --enable-rbac-authorization true \
  --enable-soft-delete true \
  --retention-days 90

# Store secrets in Key Vault
REDIS_KEY=$(az redis list-keys \
  --resource-group "$RESOURCE_GROUP" \
  --name "$REDIS_CACHE_NAME" \
  --query primaryKey -o tsv)

az keyvault secret set --vault-name "$KEY_VAULT_NAME" --name "postgres-password" --value "$PG_ADMIN_PASS"
az keyvault secret set --vault-name "$KEY_VAULT_NAME" --name "redis-password"    --value "$REDIS_KEY"
az keyvault secret set --vault-name "$KEY_VAULT_NAME" --name "jwt-secret"        --value "$(openssl rand -base64 64)"
echo "✓ Key Vault ready — all secrets stored"

# ── 7. GitHub Actions OIDC Federation ────────────────────────────────────────
echo ""
echo "▶ [7/9] Configuring GitHub Actions OIDC (no stored secrets in GitHub)..."

# Create App Registration for GitHub Actions
APP_ID=$(az ad app create \
  --display-name "$GITHUB_APP_NAME" \
  --query appId -o tsv)

SP_ID=$(az ad sp create \
  --id "$APP_ID" \
  --query id -o tsv)

# Assign Contributor on resource group + AcrPush on ACR
az role assignment create \
  --role "Contributor" \
  --assignee-object-id "$SP_ID" \
  --assignee-principal-type ServicePrincipal \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP"

ACR_ID=$(az acr show --name "$ACR_NAME" --query id -o tsv)
az role assignment create \
  --role "AcrPush" \
  --assignee-object-id "$SP_ID" \
  --assignee-principal-type ServicePrincipal \
  --scope "$ACR_ID"

# Create federated credentials for GitHub Actions (main branch + PRs)
az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters "{
    \"name\": \"github-main\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"

az ad app federated-credential create \
  --id "$APP_ID" \
  --parameters "{
    \"name\": \"github-prs\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:pull_request\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"

# Environment-scoped credentials (for protected production environment)
for ENV in staging production; do
  az ad app federated-credential create \
    --id "$APP_ID" \
    --parameters "{
      \"name\": \"github-env-${ENV}\",
      \"issuer\": \"https://token.actions.githubusercontent.com\",
      \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:environment:${ENV}\",
      \"audiences\": [\"api://AzureADTokenExchange\"]
    }"
done

TENANT_ID=$(az account show --query tenantId -o tsv)
echo "✓ OIDC federation configured"

# ── 8. NGINX Ingress Controller on AKS ───────────────────────────────────────
echo ""
echo "▶ [8/9] Installing NGINX Ingress Controller on AKS..."
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

# Add NGINX Ingress via AKS managed add-on (simplest, supported by Microsoft)
az aks enable-addons \
  --resource-group "$RESOURCE_GROUP" \
  --name "$CLUSTER_NAME" \
  --addons http_application_routing 2>/dev/null || \
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx || true
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz \
  --set controller.replicaCount=2 \
  --wait
echo "✓ NGINX Ingress ready"

# ── 9. cert-manager (auto TLS via Let's Encrypt) ─────────────────────────────
echo ""
echo "▶ [9/9] Installing cert-manager..."
helm repo add jetstack https://charts.jetstack.io || true
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true \
  --wait

kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: devops@cyberlearnix.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
EOF
echo "✓ cert-manager + Let's Encrypt ClusterIssuer ready"

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════════"
echo " ✅ Infrastructure provisioning complete!"
echo "════════════════════════════════════════════════════════════"
echo ""
echo "📋 Add these secrets to GitHub → Settings → Secrets → Actions:"
echo ""
echo "  AZURE_CLIENT_ID      = $APP_ID"
echo "  AZURE_TENANT_ID      = $TENANT_ID"
echo "  AZURE_SUBSCRIPTION_ID= $SUBSCRIPTION_ID"
echo "  AKS_CLUSTER_NAME     = $CLUSTER_NAME"
echo "  AKS_RESOURCE_GROUP   = $RESOURCE_GROUP"
echo "  POSTGRES_PASSWORD    = (retrieve from Key Vault: $KEY_VAULT_NAME)"
echo "  JWT_SECRET           = (retrieve from Key Vault: $KEY_VAULT_NAME)"
echo "  REDIS_PASSWORD       = (retrieve from Key Vault: $KEY_VAULT_NAME)"
echo ""
echo "📋 Update helm/values-production.yaml:"
echo "  DB_HOST: ${POSTGRES_SERVER_NAME}.postgres.database.azure.com"
echo "  REDIS_HOST: ${REDIS_CACHE_NAME}.redis.cache.windows.net"
echo ""
echo "📋 Configure GitHub Environments (for production approval gate):"
echo "  GitHub → Settings → Environments → production → Required reviewers"
echo ""
echo "⚠️  PostgreSQL admin password is stored in Key Vault '$KEY_VAULT_NAME'"
echo "    and was NOT printed here for security reasons."
echo "    Retrieve it with: az keyvault secret show --vault-name $KEY_VAULT_NAME --name postgres-password"
