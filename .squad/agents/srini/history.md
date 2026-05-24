# Srini — Tech Lead History

## What I Know About This Project

### Architecture
- 10 Spring Boot microservices + 1 shared library
- Services: admin, cms, course, enrollment, form, gateway, instructor, notification, shop, user
- Build system: Gradle multi-module (settings.gradle declares all subprojects)
- Communication: REST APIs via gateway-service (no service mesh — all routing at gateway level)
- Data: **Per-service PostgreSQL databases** (each service connects to its own named DB, e.g. `cyberlearnix_shop`, `cyberlearnix_cms`, `cyberlearnix_forms`)
- Auth: **JWT** — secret managed via `JWT_SECRET` env var, validated at `gateway-service`

### Conventions
- Each service has its own `Dockerfile` and `build.gradle`
- Source layout: `src/main/java/` + `src/main/resources/`
- Shared DTOs/models live in `shared-lib/`
- DB credentials injected via env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`
- Test profile uses H2 in-memory DB with PostgreSQL compatibility mode

### Decisions Made
- Per-service database isolation adopted (bounded context data ownership)
- Gateway-only routing — no inter-service direct calls documented yet
- JWT validated at gateway level; downstream services trust the forwarded identity

### Open Questions
- Are there any direct service-to-service calls (feign/rest template) bypassing the gateway?
- Is Flyway or Liquibase used for DB migrations, or manual init scripts only?

## attendance-service — Architecture Decision 2026-05-23

### ADR: New Microservice Added
- **Decision**: Added `attendance-service` at port 8092 as the 12th microservice.
- **Reason**: Attendance tracking and Zoho Meeting integration require a dedicated bounded context; mixing into course-service or enrollment-service would violate data ownership.
- **Gateway routes**: `/api/attendance/**` and `/ws/attendance/**` → `ATTENDANCE_SERVICE_URL:http://127.0.0.1:8092`
- **DB**: `cyberlearnix_attendance` — added to `docker/postgres/init/01-create-service-dbs.sql`
- **WebSocket**: STOMP over SockJS at `/ws/attendance`. In-memory broker (no external broker needed for current scale).
- **Auth**: JWT via shared-lib `JwtTokenFilter`. Zoho webhook endpoint is public, protected by `X-Zoho-Meeting-Token` header.
- **settings.gradle**: `include 'attendance-service'` added.

### Srini Sign-Off Checklist
- [x] New service inclusion in settings.gradle
- [x] Gateway routing updated
- [x] Docker Compose service block added
- [x] PostgreSQL init script updated
- [x] Port collision check (8092 is next after instructor-service at 8091)
- [x] Shared-lib NOT modified (attendance entities are service-local)
- [x] BUILD SUCCESSFUL — `./gradlew :attendance-service:compileJava` passes
