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

---

## Learnings (Session: 2026-05-05)

### ⚠️ CRITICAL: Actual Infrastructure is K3s, NOT AKS

The production cluster is **K3s on a single bare-metal VPS**, not Azure AKS.
Everything above about Azure/AKS is aspirational/future state — the current live deployment is:

- **Server:** `145.223.22.177`, SSH port `9022`
- **Cluster:** K3s single node (`srv1639541`), 2 CPUs, 7.8GB RAM, 96GB disk
- **Registry:** `ghcr.io/cyberlearnix/`
- **Namespace:** `cyberlearnix` (all 10 services + postgres StatefulSet + redis)
- **GitOps:** ArgoCD (namespace `argocd`) — app `cyberlearnix` Synced/Healthy
- **TLS:** cert-manager + Let's Encrypt, cert `apis-cyberlearnix-tls` is Ready
- **Ingress:** `apis.cyberlearnix.com` → gateway-service:8080

### K3s Resources Built This Session

**DB Backups** (`/usr/local/bin/cyberlearnix-db-backup.sh`):
- Runs daily at 1:00 AM via cron
- Backs up all 8 DBs via `kubectl exec` into `postgres-0` pod, `pg_dump` + gzip
- 7-day retention, stored at `/var/backups/cyberlearnix-postgres/`
- Tested: all 8 DBs backed up successfully

**Server Cleanup** (`/usr/local/bin/cyberlearnix-cleanup.sh`):
- Runs daily at 2:00 AM via cron
- Cleans /tmp, journal logs, unused container images, stopped pods, apt cache

**HPA (all 10 services, namespace `cyberlearnix`):**
- gateway-service: min=1, max=4 (CPU 70%, Memory 80%)
- user/course/enrollment/shop-service: min=1, max=3
- admin/cms/form/notification/instructor: min=1, max=2
- Note: metrics show `<unknown>` initially — normal, metrics-server syncs after 5+ min

**Monitoring namespace** (4 pods, all Running as BestEffort QoS):
- Prometheus: scrapes Spring Actuator `/actuator/prometheus`, pod annotations, k8s node
- Grafana: accessible at `https://apis.cyberlearnix.com/grafana` (admin / `CyberLearnix@2026`)
- Loki: log aggregation, 7-day retention, WAL disabled (permission constraint)
- Promtail: DaemonSet, ships pod logs to Loki

**Postgres external access** (NodePort `postgres-external`, port 30432):
- Selector: `app.kubernetes.io/name=postgres` (NOT `app=postgres` — that was the bug)

**ArgoCD ingress lockdown:**
- Restricted to host `apis.cyberlearnix.com` (was `*` / any host)
- Forced HTTPS redirect enabled
- TLS using `apis-cyberlearnix-tls` cert

### Critical K3s Constraint

Node CPU requests are 100% full (2000m/2000m). **All new pods MUST be deployed with NO `resources:` spec** (BestEffort QoS — 0 CPU request). Any pod with a CPU request will fail scheduling with `Insufficient cpu`.

### Known Open Issue — Zoho Token

`admin-service` logs `{error=invalid_code}` every 5 minutes from `ZohoTokenService.doRefresh()`.
The Zoho refresh token in the `cyberlearnix-secrets` k8s secret is expired.
**Fix requires:** Re-authorize in Zoho API Console → get new refresh token → `kubectl set env deployment/admin-service ZOHO_REFRESH_TOKEN=<new> -n cyberlearnix`

### Key File Paths on Server
- Backup script: `/usr/local/bin/cyberlearnix-db-backup.sh`
- Cleanup script: `/usr/local/bin/cyberlearnix-cleanup.sh`
- Backup files: `/var/backups/cyberlearnix-postgres/`
- Cron: `crontab -l` as root

### DB Password
`lysohSpLe0Eah20T6iccn90op6s2Hg` (from `cyberlearnix-secrets` k8s secret)

