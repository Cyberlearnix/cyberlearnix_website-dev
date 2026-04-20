# Kai — DevOps Engineer History

## What I Know About This Project

### Infrastructure Stack
- **Cloud:** Azure (AKS + ACR + PostgreSQL Flexible Server + Redis Cache + Key Vault)
- **Orchestration:** AKS (Kubernetes 1.29, Azure Linux, 3-node D4s_v5, zone-redundant)
- **Registry:** `swachvegaregistry.azurecr.io` (attached to AKS via managed identity — no pull secret needed)
- **Ingress:** NGINX Ingress Controller → cert-manager → Let's Encrypt TLS
- **Namespaces:** `cyberlearnix-staging`, `cyberlearnix-prod` (both with restricted pod-security)

### CI/CD Pipeline
- **CI:** `.github/workflows/ci.yml` — PR builds with smart change detection (dorny/paths-filter)
- **Deploy:** `.github/workflows/deploy.yml` — main → staging (auto) → production (manual gate)
- **Auth:** Azure OIDC federation — NO stored credentials in GitHub Actions (zero-secret auth)
- **Build:** Root `Dockerfile` — multi-stage, parameterized via `SERVICE_NAME` build-arg
- **Caching:** Docker BuildKit registry-based layer cache (ACR)

### Helm Chart (./helm/)
- Generic chart iterates over all 10 services via `range` in templates
- Files: `Chart.yaml`, `values.yaml`, `values-staging.yaml`, `values-production.yaml`
- Templates: deployment, service, hpa (autoscaling/v2), pdb, serviceaccount, configmap, ingress
- Image tags injected per-deploy via generated `image-override.yaml` (never hardcoded)
- HPA: CPU+memory autoscaling on all high-traffic services
- PDB: minAvailable:1 on all services (survives node drain/cluster upgrades)

### Security Posture
- All containers run as UID 1001 (non-root), readOnlyRootFilesystem, no privilege escalation
- `/tmp` emptyDir volume for Spring Boot temp files
- Namespaces enforce `restricted` Pod Security Standard
- Secrets managed via GitHub Secrets → K8s Secret (never in image or ConfigMap)
- JWT_SECRET, DB passwords, Redis password stored in Azure Key Vault AND GitHub Secrets

### Service Ports (from docker-compose.yml)
| Service | Port |
|---------|------|
| gateway-service | 8080 |
| user-service | 8081 |
| course-service | 8082 |
| enrollment-service | 8083 |
| notification-service | 8084 |
| shop-service | 8085 |
| form-service | 8087 |
| cms-service | 8089 |
| admin-service | 8090 |
| instructor-service | 8091 |

### DB per Service
| Service | DB name |
|---------|----------|
| user-service | cyberlearnix_users |
| course-service | cyberlearnix_courses |
| enrollment-service | cyberlearnix_enrollments |
| shop-service | cyberlearnix_shop |
| form-service | cyberlearnix_forms |
| admin-service | cyberlearnix_admin |
| cms-service | cyberlearnix_cms |
| instructor-service | cyberlearnix_instructor |

### Required GitHub Secrets (add to repo Settings)
- `AZURE_CLIENT_ID` — from setup-azure-infra.sh output
- `AZURE_TENANT_ID` — from setup-azure-infra.sh output
- `AZURE_SUBSCRIPTION_ID`
- `AKS_CLUSTER_NAME` = cyberlearnix-aks
- `AKS_RESOURCE_GROUP` = cyberlearnix-rg
- `POSTGRES_PASSWORD` — from Azure Key Vault
- `JWT_SECRET` — from Azure Key Vault
- `REDIS_PASSWORD` — from Azure Key Vault

### Pre-deploy Prerequisite
- Add `management.endpoint.health.probes.enabled=true` and
  `management.endpoints.web.exposure.include=health` to each service's `application.properties`
  (required for Kubernetes liveness/readiness/startup probes)

