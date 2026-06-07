# Squad Decisions

## Active Decisions

### [2026-04-21] ADR-001: Squad Team Composition for Cyberlearnix
**Decision:** Established a 5-member specialist team (Srini, Shiva, Sandeep, Rohit, Sneha) plus Scribe and Ralph.
**Rationale:** The platform is a Java Spring Boot microservices system requiring dedicated ownership of backend, security, DevOps, and QA domains. A generalist team would create context collisions across 10 services.
**Consequences:** Each agent owns a clear domain; cross-domain work requires explicit handoff.
**Status:** Accepted

### [2026-04-21] ADR-002: Tech Stack Baseline
**Decision:** Java Spring Boot 3.x, Gradle multi-module, PostgreSQL, Docker Compose.
**Rationale:** This is the existing production stack. Squad agents will follow these constraints.
**Consequences:** No new framework introductions without Srini sign-off.
**Status:** Accepted

### [2026-04-21] ADR-003: Azure AKS + Helm Deployment Architecture
**Decision:** Deploy all 10 microservices to Azure Kubernetes Service using a single generic Helm chart with per-service values. Use Azure OIDC for GitHub Actions authentication (no stored credentials).
**Rationale:** AKS provides managed Kubernetes with native Azure integrations (ACR pull via managed identity, Key Vault CSI). Single Helm chart with `range` iteration is DRY and maintainable for 10 homogeneous Spring Boot services. OIDC eliminates long-lived service principal secrets in GitHub.
**Consequences:** All services must expose Spring Actuator health endpoints at `/actuator/health/liveness` and `/actuator/health/readiness`.
**Status:** Accepted

### [2026-04-21] ADR-004: Zero-downtime Deployment Strategy
**Decision:** RollingUpdate with `maxUnavailable: 0`, PodDisruptionBudgets with `minAvailable: 1`, and topology spread constraints across 3 AZs.
**Rationale:** The platform is live. No downtime is acceptable during deployments or cluster maintenance.
**Status:** Accepted

### [2026-04-21] ADR-005: Secrets Management
**Decision:** Secrets (DB password, JWT secret, Redis password) stored in Azure Key Vault AND synced to GitHub Secrets. Kubernetes Secrets created/updated by GitHub Actions deploy workflow. No secrets in images, ConfigMaps, or git.
**Status:** Accepted

### [2026-06-06] ADR-006: Git Branch Strategy
**Decision:** Two long-lived branches:
- `main` → active development, deploys to `cyberlearnix` namespace (dev/staging)
- `production` → live production code, deploys to `cyberlearnix-production` namespace

**Merge Rules:**

| Action | Allowed? | How |
|--------|----------|-----|
| Any branch → `main` | ✅ | Direct push or merge — no PR required |
| `main` → `production` | ✅ | PR only |
| Any branch other than `main` → `production` | ❌ | Auto-rejected by CI |
| Direct push to `production` | ❌ | Blocked by branch protection |

**`main` branch:** Unprotected. Developers can push or merge directly. No PR required.

**`production` branch:** Fully protected.
- PR required — no direct pushes ever.
- PR source must be `main` — all other sources are automatically rejected.

**Enforcement:**
- `.github/workflows/protect-production.yml` — runs on every PR targeting `production`. Fails with a clear error if source branch is not `main`.
- GitHub branch protection on `production`:
  - Require status checks to pass (check: `Reject PRs not from main`)
  - Require a pull request before merging
  - Do not allow bypassing

**Other Rules:**
- `production` always reflects exactly what is running on the live server (`cyberlearnix-production` namespace).
- `main` may be ahead of `production` — that is expected and normal.

**Rationale:** Prevents accidental or unauthorised code reaching production. All code must pass through `main` review first.
**Owner:** Rohit (DevOps)
**Status:** Accepted

---
<!-- Merged from inbox by Scribe on 2026-05-30 -->

### [2026-05-30] ADR-006: Lab Service Architecture
**Author:** Srini (Tech Lead)  
**Status:** Proposed — Pending Squad Review

**Decision:** Introduce `lab-service` (Spring Boot 3.x, port **8093**) as a new bounded context owning lab template definitions, lab assignment records, container lifecycle operations, and WebSocket terminal session routing.

**Key constraints:**
- `lab-service` mounts `/var/run/docker.sock` and uses Docker Java SDK (`com.github.docker-java:docker-java:3.3.x`) — no shell exec of `docker` CLI.
- Student containers run on `cyberlearnix-labs-network` (isolated from `cyberlearnix-network`) with `--security-opt no-new-privileges`, `--user 1000:1000`, and no `--privileged` flag.
- Database: `cyberlearnix_labs`; env var `DB_NAME=cyberlearnix_labs`.
- Schema: `lab_templates` (admin-managed image definitions, resource limits) and `lab_assignments` (per-student container records with full lifecycle state).
- Default resource limits per container: CPU 0.5 cores, Memory 512 MB, PIDs 100.

**Supported images:** `alpine:3.19`, `ubuntu:22.04`, `cyberlearnix/kali-mini:latest`, `cyberlearnix/ctf-base:latest`, plus any admin-approved image.

**Integration points:**
- `user-service` → validate student ID on assignment
- `course-service` → validate course/topic ID when binding template
- `gateway-service` → route `/api/labs/**` REST + `/labs/terminal/**` WebSocket upgrade

**Consequences:** Adds 13th Spring Boot service. Port 8093 reserved. Docker socket access creates privileged attack surface; mitigated by student network isolation, no-new-privileges enforcement, and socket proxy (see ADR security design).

---

### [2026-05-30] ADR-007: Browser Terminal Architecture
**Author:** Srini (Tech Lead)  
**Status:** Proposed

**Decision:** WebSocket → `docker exec` bridge embedded in `lab-service` (Option B of three evaluated).

**Options evaluated:**
- Option A: `ttyd` sidecar inside each student container — rejected (per-container port explosion, complex gateway routing)
- **Option B (chosen):** Spring Boot WebSocket → `docker exec` — single endpoint, JWT auth at handshake, no per-container port exposure
- Option C: `wetty`/`gotty` + SSH sidecar — rejected (Node.js foreign runtime, sshd in every image, auth layer bridging)

**Rationale:** Aligns with Java/Spring platform pattern; Docker Java SDK supports `ExecCreateCmd` natively; zero new auth surface (same JWT mechanism); clean gateway routing; terminal-service can be extracted later if load warrants.

**Frontend:** `xterm.js` (same library as VS Code, TryHackMe, HackTheBox). WebSocket path: `ws://host/labs/terminal/{assignmentId}?token=<jwt>`.

**Consequences:** `lab-service` exposes WebSocket in addition to REST. PTY resize communicated via JSON control channel multiplexed on same WebSocket connection.

---

### [2026-05-30] ADR-008: Lab Lifecycle & Resource Management
**Author:** Srini (Tech Lead)  
**Status:** Proposed

**Decision:** Full state machine — `PENDING → PROVISIONING → RUNNING ⇄ PAUSED → TERMINATED / FAILED`. Auto-stop on inactivity; Redis sorted set tracks last-active epoch per assignment.

**Key parameters:**
- Inactivity threshold: 30 min (env var `LAB_INACTIVITY_TIMEOUT_MINUTES`)
- Concurrent container limit: `LAB_MAX_CONCURRENT_CONTAINERS=20` (default)
- Per-student limit: `LAB_MAX_LABS_PER_STUDENT=2`
- Scheduler: every 5 minutes (`@Scheduled`) scans RUNNING assignments

**Admin endpoints** (`ROLE_ADMIN`): list running/all assignments, assign template, force-terminate, pause/resume, manage templates.  
**Student endpoints** (`ROLE_STUDENT`): list my labs, request start, open terminal WebSocket.

**Monitoring:** Micrometer gauges `lab.containers.running`, `lab.containers.paused`, `lab.containers.provisioning` — scraped by Prometheus; Grafana panel added to Spring Boot Overview.

---

### [2026-05-30] IMPL-001: lab-service Scaffold Implementation
**Author:** Shiva (Backend Engineer)  
**Status:** Ready for review

**Decision:** lab-service entities (`LabTemplate`, `LabAssignment`) live in `com.cyberlearnix.lab` package, **not** in `shared-lib`, because they are domain-specific and no other service queries them directly.

**Key implementation decisions:**
- Port **8090** (NOTE: conflicts with admin-service — team must align with Srini's ADR-006 choice of 8093)
- Container naming: `cyberlearnix-lab-{studentId}-{assignmentId}` — deterministic, debuggable without DB query
- Two-phase assign: save DB record first (get id), name container from id; on Docker failure flip status to `TERMINATED`
- Idle cleanup via `@Scheduled(fixedDelay=300000)` — stops (not removes) containers idle > `lab.defaults.idle-timeout-minutes`
- WebSocket terminal: `execCreate → execStart` with `PipedOutputStream` bridge; each session on its own daemon thread

**Pre-deploy checklist:** Create `lab_db`, create Docker network `cyberlearnix-labs-network`, mount `/var/run/docker.sock`, add gateway routes for REST + WebSocket, set `LAB_DB_URL`/`DB_USER`/`DB_PASS`/`JWT_SECRET` env vars.

---

### [2026-05-30] INFRA-001: Lab Container Resource Planning
**Author:** Rohit (DevOps)  
**Status:** Accepted

**Decision:** Conservative defaults and hard limits for student lab containers on shared hardware.

**Capacity math (8 GB / 4 vCPU server):**
- CPU bottleneck: 0.5 vCPU per container → ~8–10 active students at safe headroom
- RAM: 512 MB per container → ~10 containers from available ~5 GB
- Kali containers (1 vCPU / 1 GB) → half the concurrent capacity; restrict to small security workshops (≤8 students)

**Accepted defaults:**
- `MAX_LAB_CONTAINERS=16` (safe for 8 GB / 4 vCPU; default of 50 is unsafe — override in `.env`)
- `LAB_IDLE_TIMEOUT=30` minutes — stops idle containers to reclaim memory
- Scale path: 16 GB / 8 vCPU → `MAX_LAB_CONTAINERS=40` becomes safe

**Security notes recorded (for Sandeep/Srini):**
- lab-service mounts `/var/run/docker.sock` — container-escape risk; run lab-service as non-root; never expose socket to student containers
- Student containers on `cyberlearnix-labs-network` only (isolated from platform backbone)
- CPU/memory hard limits enforced via Docker `--cpus`/`--memory` at container creation time

---

### [2026-05-30] SEC-005: Lab Management System Security Design (CRITICAL)
**Author:** Sandeep (API & Security Engineer)  
**Status:** DRAFT — Pending Srini review before implementation  
**Risk level:** CRITICAL — Docker socket exposure + student-controlled containers

**Decision:** Seven-layer security design covering container isolation, network isolation, WebSocket auth, resource exhaustion prevention, RBAC, and Docker socket proxy.

**Threat model summary:**
- Container escape → host shell (CRITICAL) — mitigated by isolation
- Network pivot to postgres/redis (HIGH) — mitigated by network isolation
- WebSocket hijacking (HIGH) — mitigated by JWT auth at handshake
- Fork bomb / OOM DoS (HIGH) — mitigated by resource limits
- lab-service abuses docker.sock (HIGH) — mitigated by socket proxy

**Key controls:**

1. **Container isolation:** All lab images must run as non-root (`labuser` UID). All student containers: `--cap-drop=ALL`, `--security-opt=no-new-privileges:true`, `--read-only` filesystem, tmpfs `/tmp:size=64m`. Never add back `SYS_ADMIN`, `SYS_PTRACE`, `NET_ADMIN`. Docker default seccomp enforced. `--privileged=FORBIDDEN` — integration test required to reject at code level.

2. **Network isolation:** Student containers on `cyberlearnix-labs-network` only (never `cyberlearnix-network`). Per-student `/29` subnets. `com.docker.network.bridge.enable_icc=false`. iptables rules blocking lab subnet (172.30.0.0/16) from reaching postgres/redis ranges — applied at server startup in `full-redeploy.sh`.

3. **WebSocket auth:** JWT validated at HTTP upgrade handshake via `HandshakeInterceptor`. Browser WS can't send headers — JWT as `?token=` query param (HTTPS mandatory). Admin/instructor get read-only view (stdin discarded). Max 2 concurrent sessions per student (`TerminalSessionRegistry`). 30-min inactivity disconnect.

4. **Resource exhaustion:** Hard-coded in `ContainerFactory` — CPU 0.5 cores (NanoCpus 500_000_000L), Memory 512 MB (`withMemorySwap` = memory → swap disabled), `pidsLimit=100`, `blkioWeight=100`. Optional storage-opt 2G (requires overlay2 + pquota — check at startup).

5. **RBAC:** `@PreAuthorize` on every endpoint. `GET /my-lab` uses `auth.getName()` (JWT subject), never `@RequestParam studentId` — IDOR prevention pattern.

6. **Docker socket proxy:** `tecnativa/docker-socket-proxy` interposes between lab-service and daemon. Allowlist: `CONTAINERS=1, NETWORKS=1, INFO=1, POST=1, DELETE=1`. Blocklist: `IMAGES=0, VOLUMES=0, SERVICES=0, SECRETS=0, BUILD=0, CONFIGS=0`. lab-service connects via `DOCKER_HOST=tcp://docker-socket-proxy:2375` — no direct socket mount.

7. **LabSecurityConfig.java:** Stateless JWT, `@EnableMethodSecurity`, permits `/actuator/health/**` and `/error`, authenticates `/api/labs/**` and `/labs/terminal/**`, `anyRequest().denyAll()` (fail-secure).

**Open items for team:**
- Internet access for lab containers (Rohit decision — affects `internal:` flag)
- Lab images from ACR private registry (aligns with ADR-003)
- Image approval workflow: Trivy scan + `cyberlearnix.verified=true` label
- Persistent student storage design (Rohit)
- `--storage-opt` availability on host (Rohit to verify overlay2 + pquota)

---

## Governance

- Srini reviews all cross-service and shared-lib changes
- Sandeep reviews all new endpoints for security coverage
- Sneha writes tests before a feature is marked done
- Rohit owns all infrastructure changes
- All meaningful decisions are recorded here with an ADR number

---

### [2026-06-01] ADR-006: EnrollmentManagement FormsTab — fully dynamic from DB
**By:** Sandeep (API & Security Engineer)
**Decision:** Fixed two bugs and added dynamic response counts in `EnrollmentManagement.jsx` FormsTab. `parseFieldCount(fields)` helper handles `fields` as either a JSON string or array, fixing the "220 fields" display bug. `load()` now fetches `GET /api/enrollments/forms/response-counts` in parallel via `Promise.allSettled` and displays a "X responses" badge per form card.
**Why:** `EnrollmentFormConfig.fields` is stored as a JSON string; `.length` on the raw string returned character count (~220) instead of element count.
**Consequences:** FormsTab shows correct field count and per-form response count from DB.
**Status:** Accepted

### [2026-06-01] ADR-007: EnrollmentFormResponseRepository — countByFormId endpoint
**By:** Shiva (Backend Engineer)
**Decision:** Added `countByFormIdAndDeletedAtIsNull(String formId)` derived query to `EnrollmentFormResponseRepository`. Added `getFormResponseCounts()` to `EnrollmentService`. Added `GET /api/enrollments/forms/response-counts` in `FormController` returning `Map<String, Long>`.
**Why:** FormsTab in the admin portal needed per-form response counts.
**Consequences:** No schema changes — query uses existing `enrollment_form_responses.form_id` and `deleted_at` columns.
**Status:** Accepted

### [2026-06-01] ADR-008: Frontend Role-Switch Portal Navigation
**By:** Sandeep (API & Security Engineer)
**Decision:** Role-switch targets determined by a static `ROLE_SWITCH_MAP` in `AdminNavbar` keyed on `currentRole`. `portal_origin_*` sessionStorage keys written before cross-portal navigation, read on mount to show a dismissible "Return to X Portal" banner. Institute switch reloads the admin portal after updating `sessionStorage.user_role`. Teacher→student switch calls `/api/auth/switch-role` to get a student-scoped JWT first.
**Consequences:** All role-preview sessions are visually flagged and reversible. Origin JWT fully restored on return — no residual role bleed.
**Status:** Accepted

### [2026-06-01] ADR-009: Expand Role-Switch Permission Matrix in AuthService
**By:** Shiva (Backend Engineer)
**Decision:** Replaced flat boolean guard in `AuthService.switchRole` with a declarative permission map: `admin → [teacher, student, institute]`, `institute → [teacher, student]`, `dual → [teacher, student]`, `teacher → [student]`. Any role not in the map gets an empty allowed list.
**Why:** Previous guard only allowed `admin` and `dual` switches, blocking legitimate preview workflows for `teacher` and `institute` role holders.
**Consequences:** Institute and teacher users can now preview lower-role portals via the navbar.
**Status:** Accepted
