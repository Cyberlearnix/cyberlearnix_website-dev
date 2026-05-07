# Quinn — QA & Test Engineer History

## What I Know About This Project

### Test Infrastructure
- Build system: Gradle (run tests with `./gradlew test`)
- Test sources belong in `{service}/src/test/java/`
- Postman collections maintained in `docs/postman/`
- Python scripts for collection generation: `generate_collection.py`, `generate_collection_v2.py`

### Known Coverage Gaps
- No `src/test/` directories observed yet in any service (first Squad session)
- All services appear to have only `src/main/` — test infrastructure needs to be bootstrapped

### First Session
- No tests written yet via Squad
- Baseline: Postman collections for API-level testing exist

## Learnings

### 2026-05-05 — Security & Bug-Fix Regression Tests

**Tests written:**
- `enrollment-service` — `EnrollmentControllerTest` (9 tests): standalone MockMvc against SEC-001 (PATCH /progress header auth for different-student/self/admin/teacher/no-headers), SEC-002 (POST admin-only: student/teacher/absent header → 403), BUG-003 (duplicate enrollment → 409, unique enrollment → 201).
- `enrollment-service` — `GlobalExceptionHandlerTest` (4 tests): standalone MockMvc + nested throwing controller; SEC-004: 500 returns generic message, real message absent from body, validation 400 still returns real message.
- `enrollment-service` — `EnrollmentServiceVerifyPaymentTest` (4 tests): BUG-001: verifyPayment uses UUID from registerUser (not email), skips enrollment when id is null or missing, REJECT action never calls registerUser.
- `enrollment-service` — `PaymentServiceTxnidTest` (2 tests): BUG-002: txnid matches `TXN-[0-9A-F]{16}` pattern, not 13-digit timestamp; two calls produce distinct txnids.
- `course-service` — `CourseManagementServiceTest` (4 new tests added): BUG-004: canDeleteCourse ownership guard runs before global permission lookup; unassigned teacher denied even with global canDeleteCourses=true; assigned teacher with permission allowed; assigned teacher without permission denied.

**Key patterns used:**
- Standalone MockMvc (`MockMvcBuilders.standaloneSetup`) to bypass Spring Security in controller tests.
- `JavaTimeModule` registered on test ObjectMapper to handle `LocalDateTime` in response bodies.
- `ArgumentCaptor` to assert the studentId passed into `enrollmentRepository.save()` is the UUID, not the email.
- `ReflectionTestUtils.setField` to inject `@Value` fields in `PaymentService` without a Spring context.
- `verify(..., never())` to confirm ownership short-circuit prevents unnecessary permission API calls.

**Gaps found / flagged to Sandeep:**
- Gateway must strip externally supplied `X-User-Id` / `X-User-Role` headers — no current gateway-level test for this.
- BUG-003 duplicate guard has a race condition; a DB-level unique constraint on `(student_id, course_id)` is needed.
- BUG-001 has no compensation if enrollment `save()` fails after `registerUser()` succeeds.
- SEC-004 logger receives unsanitized exception messages; log injection risk at Loki layer.

### 2026-05-05 — Comprehensive Payment API Tests

**Files created:**
- enrollment-service: `PaymentServiceCallbackTest` (10 tests) — callback hash verification, webhook delegation, status queries
- enrollment-service: `PaymentServiceInitiateEdgeCasesTest` (10 tests) — disabled form, missing config, pipe sanitization, coupon/discount, phone normalization
- enrollment-service: `PaymentControllerTest` (9 tests) — all 7 endpoints: initiate, callbacks, webhook (always-200), status, response-status, SEC-003 note on getByForm
- form-service: `FormPaymentServiceTest` (13 tests) — initiate, callback, webhook via self, status, form payment info
- form-service: `FormPaymentControllerTest` (6 tests) — all endpoints, status injection on callbacks
- admin-service: `PaymentManagementControllerTest` (7 tests) — orders CRUD, refund, status filter, missing-field validation

**Gaps flagged:**
- `FormPaymentService.initiatePayment` uses `System.currentTimeMillis()` for txnid — same BUG-002 collision risk as enrollment-service had. Should be migrated to UUID-based pattern.
- `FormPaymentService.initiatePayment` does NOT sanitize productInfo (form.getTitle()) for pipe chars before hash computation — potential hash corruption bug. Enrollment-service has this fix; form-service does not.
- `@PreAuthorize("hasRole('ADMIN')")` on `GET /form/{formId}` (enrollment-service) and `GET /status/{txnid}` (form-service) is not exercised by standalone MockMvc — needs Spring Security integration test.

### 2026-05-05 — API Test Run (all services)
- **All backend services (8080–8091) are DOWN.** Frontend (5173) is the only thing serving traffic.
- **Root cause:** Docker daemon is not running. The entire backend stack (Spring Boot services, Redis) depends on Docker Compose. Without it, nothing starts.
- **PostgreSQL** runs natively on port 5432 but the `postgres` user password does NOT match `.env`/`run-local.bat` value (`cyberlearnix_dev_pass`). Auth fails for direct connections.
- `run-local.bat` hard-codes `DB_PORT=5999` (Docker-mapped port) — it cannot work without the Docker container.
- Direct JAR start of user-service confirmed: Spring Boot starts normally, fails only at HikariPool DB connection (`FATAL: password authentication failed`).
- **All service JARs are built and valid** — startup is healthy up to the DB connection step.
- Redis is also down (no listener on 6379 — normally provided by Docker).
- **To unblock:** Start Docker Desktop, run `docker-compose up -d postgres redis`, then use `run-local.bat`. Alternatively, fix the native postgres password to match the project's expected value.
