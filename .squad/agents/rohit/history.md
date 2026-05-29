# Kai — DevOps Engineer History

## Learnings

### [2026-05-13] Gateway Routing Fixes
- Added `admin-stats-users` route (→ user-service:8081 `/api/admin/stats/users`) and `admin-stats-courses` route (→ course-service:8082 `/api/admin/stats/courses`) **before** the generic `admin-service` catch-all in `gateway-service/src/main/resources/application.yml`. Spring Cloud Gateway uses first-match-wins — specific routes must precede wildcards.
- `activity-service` route covering `/api/activity/**` was already present — no change needed.
- `ADMIN_SERVICE_URL`, `CMS_SERVICE_URL`, `INSTRUCTOR_SERVICE_URL` were already defined in `docker-compose.yml` gateway environment block — no change needed.
- All default fallback ports in `application.yml` (8081–8091) match the ports in `docker-compose.yml` — no mismatches found.
- Key file: `gateway-service/src/main/resources/application.yml`

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

### [2026-05-30] Lab Service Infrastructure Added

**Changes made:**
- `docker-compose.yml`: Added `lab-service` block (port **8093** — note: 8090 was already taken by `admin-service`). Added `LAB_SERVICE_URL` env var to gateway-service. Added `lab-service: condition: service_started` to gateway-service `depends_on`. Added `cyberlearnix-labs-network` (isolated bridge) to `networks:` section.
- `docker/postgres/init/03-lab-db.sql`: New init script creates `lab_db`, tables `lab_templates` + `lab_assignments`, and seeds 3 default templates (Alpine, Ubuntu, Kali).
- `settings.gradle`: Added `include 'lab-service'`.
- `.env.example`: Added `MAX_LAB_CONTAINERS=50` and `LAB_IDLE_TIMEOUT=30`.
- `gateway-service/src/main/resources/application.yml`: Added `lab-service` route (`/api/labs/**`) and `lab-service-terminal` route (`/labs/terminal/**` with WebSocket Upgrade header) after the attendance-service block.

**Key decisions:**
- Port 8093 assigned to lab-service (8090 already used by admin-service — the request spec had an error).
- `cyberlearnix-labs-network` is a separate Docker network so student containers are isolated from `cyberlearnix-network` (platform services).
- Docker socket mount (`/var/run/docker.sock`) is limited to lab-service only; student containers must NOT have socket access.
- Resource planning doc written to `.squad/decisions/inbox/rohit-lab-docker-plan.md`. Conservative recommendation: `MAX_LAB_CONTAINERS=16` on 8 GB / 4 vCPU.

**Port table update:**
| Service | Port |
|---------|------|
| attendance-service | 8092 |
| lab-service | 8093 |

---

### [2026-05-30] Cross-team Update: Sandeep's Docker Socket Proxy Recommendation

**From:** Sandeep's security design (LAB-SEC-001, `.squad/decisions.md` SEC-005)

Sandeep has flagged that direct `/var/run/docker.sock` mount into lab-service is the **highest-risk element** in the platform. His recommendation (accepted in SEC-005):

**Add `tecnativa/docker-socket-proxy` to docker-compose.yml:**

```yaml
docker-socket-proxy:
  image: tecnativa/docker-socket-proxy:latest
  container_name: cyberlearnix-docker-proxy
  environment:
    CONTAINERS: 1      # allow: container lifecycle ops
    NETWORKS: 1        # allow: per-student network creation
    INFO: 1            # allow: docker info (driver checks)
    POST: 1            # allow: POST methods (create/start/stop)
    DELETE: 1          # allow: DELETE (container removal)
    IMAGES: 0          # BLOCK: no image pull/push
    VOLUMES: 0         # BLOCK: no volume create/delete
    SERVICES: 0        # BLOCK: no swarm service manipulation
    SECRETS: 0         # BLOCK: no secret access (hides env of other containers)
    BUILD: 0           # BLOCK: no docker build
    CONFIGS: 0         # BLOCK
    TASKS: 0           # BLOCK
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock:ro
  networks:
    - cyberlearnix-network
  restart: unless-stopped
  privileged: true  # proxy itself needs privileged to read the socket — acceptable
```

**lab-service must then use `DOCKER_HOST=tcp://docker-socket-proxy:2375`** instead of a direct socket mount.

**Action required (Rohit):**
- [ ] Add `docker-socket-proxy` service to `docker-compose.yml`
- [ ] Remove `/var/run/docker.sock` mount from lab-service in `docker-compose.yml`
- [ ] Add `DOCKER_HOST=tcp://docker-socket-proxy:2375` env var to lab-service block
- [ ] Verify overlay2 + pquota availability on host (for `--storage-opt size=2G`)
- [ ] Add iptables egress rules to `full-redeploy.sh` blocking lab subnet (172.30.0.0/16) from reaching platform RFC-1918 ranges

