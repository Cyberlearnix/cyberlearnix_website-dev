# Kai — DevOps Engineer History

## Learnings

### [2026-06-06] Two-Environment Deployment Pipeline

- **Two-environment pipeline pattern:** `main` → `cyberlearnix` namespace (dev, basic-auth protected); `production` → `cyberlearnix-production` namespace (live, no auth).
- **Basic auth via nginx ingress:** K8s `generic` secret named `basic-auth` in the target namespace (from-file `auth=<htpasswd-file>`) + three `nginx.ingress.kubernetes.io/auth-*` annotations on each ingress. htpasswd generated on runner with `htpasswd -nbB`, base64-encoded and piped via SSH heredoc to avoid special-character escaping issues with strong passwords.
- **`deploy-production.yml` is a separate workflow** triggered on `production` branch. No change-detection job — production always does a full deploy of all 12 services. Uses `prod-latest` + `sha` tags (separate from dev's `latest` + `sha` tags).
- **`ci-cd.yml` deploy job renamed** to `deploy-dev`, `environment: dev`, namespace hardcoded to `cyberlearnix` (removed `K3S_NAMESPACE` secret reference).
- **Security rule:** Raw password from `DEV_BASIC_AUTH_PASSWORD` secret stays in runner env var only — never echoed. Only the bcrypt hash (base64-encoded) travels over SSH.

### [2026-06-05] Lab Pre-Installation Build Pipeline — DockerClientService + LabImageBuildService

**New files created:**
- `lab-service/src/main/java/com/cyberlearnix/lab/entity/SetupStatus.java` — enum: `NOT_CONFIGURED`, `BUILDING`, `STAGED`, `ACTIVE`, `FAILED`
- `lab-service/src/main/java/com/cyberlearnix/lab/service/LabImageBuildService.java` — async build service with SSE streaming

**Files modified:**
- `CourseLabConfig` — added 5 new fields: `setupScript`, `setupStatus`, `setupLog`, `stagedDockerImage`, `activeDockerImage`
- `CourseLabConfigRepository` — added `Optional<CourseLabConfig> findByCourseId(Long courseId)`
- `DockerClientService` — added `createSetupContainer`, `execScript`, `commitContainer` methods; added imports for `ExecCreateCmdResponse`, `Frame`, `ResultCallback`
- `LabServiceApplication` — added `@EnableAsync`

**Key import paths for docker-java 3.3.4:**
- `com.github.dockerjava.api.command.ExecCreateCmdResponse`
- `com.github.dockerjava.api.model.Frame`
- `com.github.dockerjava.api.async.ResultCallback` (contains inner `Adapter<A_RES_T>`)

**Convention — build pipeline flow:**
1. Admin saves script → `SetupStatus.NOT_CONFIGURED`
2. Admin triggers build → `BUILDING` → async exec in temp container → commit image → `STAGED`
3. Admin clicks Publish → `ACTIVE`; `activeDockerImage` is set; students use this image
4. On build failure, `activeDockerImage` is **not** touched — students keep previous working image

**SSE approach:** In-memory `ConcurrentHashMap` per courseId buffers logs for reconnect replay. `SseEmitter` timeout set to 600 000 ms (10 min) to cover long-running setup scripts.

### [2026-06-04] Media API — Helm Drive Credential Wiring Fixes

**Bugs fixed in `helm/templates/deployment.yaml`:**

1. **`user-service` missing from Drive credentials injection** — The Helm `if or` condition only covered `course-service`, `cms-service`, `lab-service`. `user-service` has `PhotoUploadController` that needs `GoogleDriveService` (and has `google.drive.*` in `application.properties` + Drive env vars in `docker-compose.yml`). Without this fix, profile photo uploads always returned 503 on K3s/production. Added `user-service` to the condition.

2. **Duplicate `optional:` YAML keys** — Both `MAIL_PASSWORD` and `RESEND_API_KEY` secretKeyRef blocks had two `optional:` keys (`true` then `false`). In Go's yaml.v3 last value wins → `optional: false`, causing pods to crash if secrets are absent. Removed the duplicate `optional: false` lines, leaving only `optional: true`.

**Convention:** Any service that uses `GoogleDriveService` from `shared-lib` needs `GOOGLE_DRIVE_CREDENTIALS_JSON_B64` + `GOOGLE_DRIVE_FOLDER_ID` injected in the Helm deployment `if or` block AND in `docker-compose.yml` environment block AND has `google.drive.*` properties in `application.properties`.

### [2026-05-30] Course-Linked Labs — DB Schema + Docker Deploy (Rohit)
- **New tables in lab_db:** `course_lab_configs`, `lab_approval_requests` — added alongside existing `lab_templates` and `lab_assignments` (which gained `course_id` + `approval_request_id`). 5 indexes added for query performance.
- **lab-service port:** runs internally on **8090** (set in `application.yml`). Docker-compose exposed it as `8093:8090` (host 8093 → container 8090). The gateway uses `http://lab-service:8090` internally. Historical history.md had 8090 listed for admin-service — that's the host-accessible port for admin; lab-service is 8093 externally.
- **Bug fixed — JAXB on Java 21:** `docker-java:3.3.4` transitively pulls `jackson-module-jaxb-annotations` which needs `javax.xml.bind.*` (removed from JDK since Java 9). Fix: add `javax.xml.bind:jaxb-api:2.3.1` to `lab-service/build.gradle`.
- **Bug fixed — docker-compose env mismatch:** lab-service used `LAB_DB_PASSWORD=${POSTGRES_PASSWORD}` but `application.yml` reads `${DB_PASS}`. Fix: also pass `DB_PASS=${POSTGRES_PASSWORD}` in docker-compose.
- **Gateway `/api/labs/**` already broad enough** — covers all new sub-paths (`/courses/**`, `/admin/approvals/**`, `/my-labs/**`). No gateway change needed.
- lab-service smoke test: `GET http://localhost:8093/actuator/health` → `{"status":"UP"}`.

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

### [2026-06-02] Lab Service CrashLoopBackOff — Root Cause & Fix

**Symptom:** `lab-service-68896b7578-gt7z6` stuck at `0/1 CrashLoopBackOff`, 74 restarts in 38h. All `/api/labs/**` returning 404 from Spring Cloud Gateway (no healthy endpoint to route to).

**Diagnosed via:** `kubectl logs` on production pod (K3s, port 9022).

**Root Cause:** `LabServiceApplication` scanned only `com.cyberlearnix.lab.*`. `GoogleDriveService` in `com.cyberlearnix.shared.service` (shared-lib) was never registered — `UnsatisfiedDependencyException` on startup.

**Fix:** Added `scanBasePackages = {"com.cyberlearnix.lab", "com.cyberlearnix.shared.service"}` to `LabServiceApplication`. This is the same pattern `cms-service` and `course-service` already use. Commit `311c532` on `develop` — needs PR merge to trigger CI-CD rebuild + ArgoCD redeploy.

**Project Convention:** Any service that uses `GoogleDriveService` or `CloudinaryService` from `shared-lib` MUST include `"com.cyberlearnix.shared.service"` in `scanBasePackages`. Do NOT use `"com.cyberlearnix.shared"` (too broad — pulls in `SharedSecurityConfig`).

---

### [2026-06-01] Lab Service API Fix — Post-Deploy Bugs

**Root causes diagnosed and fixed:**

1. **Duplicate YAML keys in `helm/values-k3s.yaml` `images:` section** — Commits #120 and #121 (ImagePullBackOff fix) both appended a full second block of 12 image tags, creating invalid YAML with duplicate keys. Go's yaml.v3 silently uses the last value; ArgoCD may behave inconsistently. Fixed: collapsed to a single canonical block. The CI `sed` regex still works correctly (matches both entries, updates both to same SHA).

2. **`LAB_SERVICE_URL` wrong default port in `gateway-service/src/main/resources/application.yml`** — Default was `http://127.0.0.1:8093` (the host-exposed port); should be `8090` (the container/service port). In K3s, `LAB_SERVICE_URL=http://lab-service:8090` overrides the default, so this was a latent bug that only manifests in local dev fallback mode.

3. **`lab_db` and `cyberlearnix_attendance` missing from postgres init ConfigMap** (`helm/templates/postgres.yaml`). These two databases were not in the 01-create-databases.sql ConfigMap. The per-service init container creates them idempotently, but the postgres init SQL is the belt-and-suspenders fallback. Added both.

4. **`LAB_DB_URL` not explicitly set in Helm deployment** — lab-service uses `${LAB_DB_URL:jdbc:postgresql://postgres:5432/lab_db}` for its datasource URL, not the injected `DB_HOST`/`DB_PORT`. Added explicit `LAB_DB_URL` env var in the deployment template using `$.Values.appConfig.DB_HOST` and `DB_PORT` so the DB host stays in sync with the rest of the cluster.

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

