# Aria — API & Security Engineer History

## What I Know About This Project

### Security Architecture
- JWT-based authentication (documented in SECURITY_IMPLEMENTATION_GUIDE.md)
- Inactivity logout implemented (see INACTIVITY_LOGOUT_FEATURE.md)
- Gateway-service acts as the security entry point for all downstream services
- Admin RBAC tests exist in `docs/postman/Cyberlearnix_Admin_RBAC_Tests.postman_collection.json`

### API Collections
- Master API collection: `Master_API_Collection.json`
- Postman collections in: `docs/postman/`
- Complete API docs: `docs/COMPLETE_API_DOCUMENTATION.md`

### First Session
- Security infrastructure already in place
- No new security changes yet via Squad

## Learnings

### [2026-05-05] Post-Fix Security Audit — SEC-001 through SEC-004

**What was verified:**
- `@EnableMethodSecurity` is present on `enrollment-service/SecurityConfig.java` — `@PreAuthorize` annotations are active.
- Gateway `JwtAuthenticationFilter` correctly strips X-User-Id/X-User-Role before injection (anti-spoofing), then injects fresh values from validated JWT claims. Defense works.
- Shared `JwtTokenFilter` maps JWT claim `role: "admin"` → `SimpleGrantedAuthority("ROLE_ADMIN")` — satisfies `hasRole('ADMIN')` in @PreAuthorize correctly.
- SEC-001/002/003/004 fixes are all correctly implemented and structurally sound.

**Blockers found (not yet fixed):**
- `PUT /api/enrollments/{id}` — IDOR, no role/identity guard. Any authenticated user can overwrite any enrollment record.
- `CouponController` create/list/delete endpoints — `authenticated()` only, no admin role check. Students can manage coupons.

**Patterns to remember:**
- Always check both `@EnableMethodSecurity` AND the `JwtTokenFilter` role authority format (`ROLE_` prefix) when auditing @PreAuthorize.
- Local exception handlers in controllers bypass `GlobalExceptionHandler` — audit each catch block individually for message leakage.
- Gateway `isPublicPath()` has a GET-whitelist gap for `/api/enrollments/` paths — not everything under that prefix is truly public, relying on downstream auth as second line.

### [2026-05-30] Lab Management System — Security Design (LAB-SEC-001)

**Context:** Team building Student Lab Management System — students get individual Linux containers, accessed via browser WebSocket terminal. lab-service mounts `/var/run/docker.sock`.

**Risk verdict:** CRITICAL — this is the highest-risk feature ever added to the platform. Docker socket exposure + student-controlled containers is effectively giving students partial shell access to the host.

**Key design decisions made:**

1. **Container isolation:** Non-root user in all lab images (`adduser labuser && USER labuser`), `--cap-drop=ALL`, `--security-opt=no-new-privileges:true`, `--read-only` filesystem, Docker default seccomp. `--privileged` is FORBIDDEN — add integration test to reject it at code level.

2. **Network isolation:** Student containers on `cyberlearnix-labs-network` (separate from `cyberlearnix-network`). Per-student `/29` subnets. `com.docker.network.bridge.enable_icc=false` blocks student-to-student traffic. iptables rules to block lab subnet from reaching postgres/redis RFC-1918 ranges.

3. **WebSocket auth:** JWT validated at HTTP upgrade handshake (not after). Browser WebSocket can't send headers — JWT passed as `?token=` query param (HTTPS mandatory). Admin/instructor get read-only view (stdin discarded). Rate limit: max 2 concurrent sessions per student. 30-min inactivity disconnect.

4. **Resource limits (DoS):** CPU 0.5 cores, memory 512MB (no swap), pids-limit=100 (fork bomb protection), blkio-weight=100, optional storage-opt 2G (requires overlay2 + pquota).

5. **RBAC:** `@PreAuthorize` on every endpoint. `GET /my-lab` extracts studentId from JWT (`auth.getName()`), never from request body — IDOR prevention.

6. **Docker socket proxy:** `tecnativa/docker-socket-proxy` sits between lab-service and daemon. Allowlist: CONTAINERS=1, NETWORKS=1, INFO=1. Blocklist: IMAGES=0, VOLUMES=0, SERVICES=0, SECRETS=0, BUILD=0. lab-service uses `DOCKER_HOST=tcp://docker-socket-proxy:2375`, no direct socket mount.

7. **LabSecurityConfig.java:** Mirrors enrollment-service pattern — stateless JWT, `@EnableMethodSecurity`, permits `/actuator/health/**` and `/error`, authenticates `/api/labs/**` and `/labs/terminal/**`, `anyRequest().denyAll()` (fail-secure).

**Open questions escalated to team:**
- Internet access for lab containers? (affects `internal:` flag on network)
- Lab images from ACR (private registry) — aligns with ADR-003
- Image approval workflow (Trivy scan + `cyberlearnix.verified=true` label)
- Persistent student storage (Rohit to design)
- `--storage-opt` availability on host (Rohit to verify overlay2 + pquota)

**Patterns to remember:**
- For WebSocket security: authenticate at handshake (HTTP upgrade), not in the WS message handler. The handshake is the only HTTP request — after upgrade, Spring Security filters no longer run.
- For Docker-socket-exposed services: always interpose a socket proxy. Direct socket mount = root on host.
- For per-student resource isolation: all limits must be hard-coded in ContainerFactory, never taken from request input.
- IDOR on lab endpoints: always `auth.getName()` (JWT subject) for student identity, never `@RequestParam String studentId`.
