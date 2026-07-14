# Shiva — Backend Engineer History

## What I Know About This Project

### Service Structure
Each microservice under `{service}-service/src/main/` follows the standard Spring Boot layout.
Shared types are in `shared-lib/`. All services declare `implementation project(':shared-lib')`.

### Build
- Root build: `./gradlew build`

## Learnings (prepended)

### [2026-07-14] Lab Terminal — 4-Bug Fix (Session Closing + No Resume)

**Root cause of terminal sessions getting closed:**
`lastActiveAt` was never updated from WebSocket terminal activity. The `@Scheduled` idle-cleanup (`cleanupIdleLabs`) fires every 5 min and stops any assignment where `lastActiveAt` is older than 30 min. Since the WS handler never wrote to the DB, every active terminal session was killed 30 min after `assignLab()` / `resumeLab()` — even with constant user input.

**4 bugs fixed:**

1. **`lastActiveAt` not updated from WS activity** — `forwardToContainer()` and `afterConnectionEstablished()` now call a debounced `updateLastActiveAt(session)` that writes to the DB at most once per minute per session. `sessionLastHeartbeat` ConcurrentHashMap tracks last write time. Fixed in `LabTerminalWebSocketHandler.java`.

2. **Resize JSON forwarded to container stdin** — `handleTextMessage` was sending all text messages (including `{"type":"resize","cols":N,"rows":N}`) as raw bytes to the shell, injecting garbage. Fixed: `handleTextMessage` now checks if payload starts with `{` and contains `"type":`, and intercepts resize messages to call `dockerClient.resizeExecCmd().withSize(rows,cols).exec()` instead of forwarding. Added `handleResizeMessage()` and `extractIntField()` helpers.

3. **No "Resume Lab" in terminal.html** — Paused-lab errors showed a dead-end message. Fixed in `terminal.html`: error-box now has a `resumeBtn` (hidden by default). When `isLabError`, the Resume button is shown. `resumeAndReconnect()` calls `POST /api/labs/{id}/resume`, then auto-reconnects.

4. **No reconnect on unexpected disconnect** — Fixed in `terminal.html`: auto-reconnect with 3s/6s/10s backoff (max 3 attempts). After 3 failures, shows Resume + Retry buttons. Heartbeat: `setInterval(() => sendResize(), 4*60*1000)` keeps `lastActiveAt` fresh for read-only sessions (no typing).

**Files changed:**
- `lab-service/src/main/java/com/cyberlearnix/lab/websocket/LabTerminalWebSocketHandler.java`
- `cms-service/src/main/resources/static/student/terminal.html`

### [2026-06-12] Form Service — Lombok/Jackson Boolean Serialization Fix

**Files:**
1. [form-service/src/main/java/com/cyberlearnix/form/dto/FormRequestDTO.java](form-service/src/main/java/com/cyberlearnix/form/dto/FormRequestDTO.java)
2. [form-service/src/main/java/com/cyberlearnix/form/dto/FormResponseDTO.java](form-service/src/main/java/com/cyberlearnix/form/dto/FormResponseDTO.java)

**Changes/Fixes:**
1. Resolved a critical Lombok/Jackson serialization issue where boolean fields starting with `is` (specifically `isActive` and `isQuiz`) were serialized by default as `active` and `quiz`. This broke the front-end expectations of `"isActive"` and `"isQuiz"`, leading to active forms being treated as inactive or non-quiz because `form.isActive` was `undefined` on the UI.
2. Added Jackson's `@JsonProperty("isActive")` and `@JsonProperty("isQuiz")` annotations to compile-time force the exact JSON property naming regardless of Lombok's getter/setter generation habits.
3. Verified clean compilation and build using `./gradlew.bat :form-service:compileJava`.

### [2026-06-12] Form Service — Timezone alignment to Asia/Kolkata

**Files:**
1. `form-service/src/main/java/com/cyberlearnix/form/service/FormService.java`
2. `form-service/src/main/java/com/cyberlearnix/form/service/FormPaymentService.java`

**Changes/Fixes:**
1. Aligned the application with the platform's default timezone. Resolved the issue where the server running in UTC caused naive usage of `LocalDateTime.now()` to fail form start and end duration validations (entered in IST/Asia/Kolkata timezone).
2. Refactored all 9 occurrences of `LocalDateTime.now()` in `FormService.java` and all 2 occurrences in `FormPaymentService.java` to use `LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))` explicitly.
3. Verified compilation and ensured zero test failures in `form-service` by running `./gradlew.bat :form-service:compileJava` and `./gradlew.bat :form-service:test`.

### [2026-06-04] CMS MediaController — Drive Connection + Security Fix

**File:** `cms-service/src/main/java/com/cyberlearnix/cms/controller/MediaController.java`

**Bugs fixed:**
1. **`uploadedBy` from user-controlled query param** — Anyone could forge the uploader identity. Changed to read from `X-User-Id` request header (injected by the JWT gateway filter).
2. **HTTP 200 on upload** — Should be 201 Created. Fixed.
3. **Missing DELETE endpoint** — Added `DELETE /api/cms/media/{fileId}` that delegates to a new `MediaService.deleteMedia(fileId)` method.

**New `MediaService.deleteMedia(String fileId)`:** calls `googleDriveService.deleteFile(fileId)` then removes the matching `MediaFile` DB record (matched by fileId substring in the stored URL).

**Security note:** `cms-service/SecurityConfig` already gates `POST /api/cms/**` to `ROLE_ADMIN` — so the upload endpoint only needs the gateway-injected `X-User-Id` for audit, not a role check. The new DELETE endpoint inherits the same `ROLE_ADMIN` constraint.

### Media API — Full Status (as of 2026-06-04)

| Service | Controller | Endpoint | Drive wired? | Stream proxy? | Notes |
|---------|-----------|----------|-------------|---------------|-------|
| user-service | PhotoUploadController | `POST /api/users/upload/photo` | ✅ | N/A (returns view URL) | Auth: Spring Security Authentication |
| course-service | MaterialUploadController | `POST /api/materials/upload/*`, `GET /api/materials/drive/stream/{id}` | ✅ | ✅ | Auth: X-User-Id + X-User-Role |
| cms-service | MediaController | `GET/POST/DELETE /api/cms/media/**` | ✅ | N/A (returns view URL) | Auth: ROLE_ADMIN via SecurityConfig |
| lab-service | LabMaterialController | `POST /api/labs/materials/upload`, `GET /api/labs/materials/drive/stream/{id}` | ✅ | ✅ | Auth: PreAuthorize ADMIN/INSTRUCTOR |
- Single service: `./gradlew :{name}-service:build`
- Compiled classes go to `build/classes/`, JARs to `build/libs/`

### First Session
- No features implemented yet via Squad
- Baseline: project already has controllers, entities, and service layers in place

## Learnings

### [2026-06-02] Lab Service — CrashLoopBackOff Fix (LabServiceApplication missing scanBasePackages)

**Root Cause:** `LabServiceApplication` used plain `@SpringBootApplication` which only scans `com.cyberlearnix.lab.*`. `GoogleDriveService` lives in `com.cyberlearnix.shared.service` (shared-lib) and was never registered as a Spring bean, causing:
```
Error creating bean 'labMaterialController':
No qualifying bean of type 'com.cyberlearnix.shared.service.GoogleDriveService'
```
The service had been crash-looping (74 restarts, always `0/1 Running`) since initial deployment.

**Fix:** `lab-service/src/main/java/com/cyberlearnix/lab/LabServiceApplication.java`
```java
// Before
@SpringBootApplication

// After — matches cms-service and course-service pattern
@SpringBootApplication(scanBasePackages = {"com.cyberlearnix.lab", "com.cyberlearnix.shared.service"})
```
**Important:** Use `"com.cyberlearnix.shared.service"` NOT `"com.cyberlearnix.shared"` — the broader scan would also pull in `SharedSecurityConfig` from `com.cyberlearnix.shared.security`, conflicting with lab-service's own `SecurityConfig`.

**Committed:** `311c532` on `develop` — awaiting PR to main.

---

### [2026-06-01] Lab Service — Google Drive Integration

**Task:** Connect lab-service APIs to Google Drive (user confirmed Drive credentials already provisioned on server).

**Changes made:**
- `lab-service/src/main/resources/application.yml`: Added `google.drive.credentials-json-b64` and `google.drive.folder-id` properties backed by env vars `GOOGLE_DRIVE_CREDENTIALS_JSON_B64` / `GOOGLE_DRIVE_FOLDER_ID`.
- `docker-compose.yml` lab-service block: Added both `GOOGLE_DRIVE_CREDENTIALS_JSON_B64` and `GOOGLE_DRIVE_FOLDER_ID` env vars (optional, same pattern as user-service / course-service).
- `helm/templates/deployment.yaml`: Added `lab-service` to the existing Google Drive credentials block (`or` condition now includes `lab-service`). Secrets fetched from `cyberlearnix-secrets` with `optional: true`.
- **New file** `lab-service/.../controller/LabMaterialController.java`: Three endpoints:
  - `POST /api/labs/materials/upload` (ADMIN/INSTRUCTOR) — multipart upload to Drive, 200 MB limit, returns `fileId`/`viewUrl`/`streamUrl`.
  - `GET  /api/labs/materials/drive/stream/{fileId}` (any auth) — proxy-streams from Drive, supports HTTP Range for resumable downloads.
  - `DELETE /api/labs/materials/{fileId}` (ADMIN) — deletes Drive file.
- `GoogleDriveService` is already in `shared-lib` and `lab-service` already depends on `:shared-lib` — no new Gradle dependency needed.
- All endpoints gracefully return `503 SERVICE_UNAVAILABLE` when Drive is not configured (`isEnabled() == false`).

---

### API Audit — 2026-05-13

#### Gateway Routing Gaps (Critical)
- `admin-service` has NO routes in `gateway-service/src/main/resources/application.yml`
  - Affected: all 8 controllers at `/api/admin/**`
- `cms-service` has NO routes in the gateway at all
  - Affected: `PageController` at `/api/cms/**`, `MediaController` at `/api/cms/media/**`
- `instructor-service` has NO routes in the gateway
  - Affected: all 6 controllers at `/api/instructor/**`
- `user-service` has unmapped endpoints: `/api/admin/stats/users` and `/api/activity/logs/**`
- `course-service` has unmapped endpoint: `/api/admin/stats/courses`

#### Path Conflict
- `course-service`: `AssignmentController` and `AssignmentManagementController` BOTH declare `@RequestMapping("/api/assignments")`. No individual method collisions but sharing a base path on two separate classes is fragile and confusing.

#### Duplicate Course Creation Endpoint
- `CourseController.POST /api/courses` and `CourseManagementController.POST /api/course-management/courses` both create courses. Divergent implementations.

#### Dead Controller Method
- `form-service` `FormController.getAdminForm(String id)` has `@PreAuthorize("hasRole('ADMIN')")` but NO HTTP mapping annotation — it is unreachable as an API endpoint.

#### Security: Header-Based Auth Without `@PreAuthorize`
- `course-service`: `BannerController`, `PartnerController`, `PromoController` check `X-User-Role` header directly in method body instead of `@PreAuthorize`. If gateway doesn't strip this header, any caller can fake admin access.
- `user-service`: `SiteSettingsController` does the same.
- `shop-service`: `ShopController.updateShopSettings()` has a TODO comment: "Auth check for admin role should be implemented here or in a Filter" — NOT implemented at all.

#### Missing `@Valid` on RequestBody
- `course-service`: `CourseController`, `CourseManagementController` — `@RequestBody CourseCreateDTO` has no `@Valid`
- `user-service`: `CareerController`, `MenuController`, `TeamCollaborationController`, `ChatbotController` — `@RequestBody` entities without `@Valid`
- `enrollment-service`: `SubmissionController.createSubmission()` — raw entity body, no `@Valid`

#### Wrong HTTP Status Codes (200 instead of 201 for creation)
- `course-service`: `ExamManagementController.createExam()`, `CertificateController.issueCertificate()`, `ContentReviewController.submitForReview()`, `UpdateController.createUpdate()`, `AssignmentController.createAssignment()`
- `user-service`: `CareerController.createJob()`, `MenuController.createMenu()`, `TeamCollaborationController.createTeam()`, `ChatbotController.createResponse()`

#### Missing Global Exception Handler
- Only `enrollment-service` has a `GlobalExceptionHandler` (`@RestControllerAdvice`).
- `user-service`, `course-service`, `form-service`, `instructor-service`, `admin-service`, `cms-service`, `shop-service`, `notification-service` — none have one.

#### Anti-Pattern: Notification Controller Action Dispatcher
- `notification-service` `NotificationController` is a single `POST /api/notifications` with a `@RequestParam String action` switch-case. Violates REST principles.

#### Repository Direct in Controller
- `admin-service` `SettingsController` directly injects `SiteSettingRepository` — bypasses service layer.
- `course-service` `UpdateController`, `BannerController`, `PartnerController`, `CertificateController`, `ContentReviewController` all operate directly on repositories from the controller layer.

#### `@PostConstruct` in Controllers
- `user-service` `ChatbotController` and `MenuController` — data seeding in constructor/PostConstruct. Should be in a DataSeeder `@Component`.
- `enrollment-service` `EnrollmentWorkflowController` — same anti-pattern.

#### Async Thread in Controller
- `user-service` `ContactSubmissionController.createSubmission()` spawns `new Thread()` for async email. Should use `@Async` with a configured `TaskExecutor`.

#### User Controller Returns List, Not ResponseEntity
- `admin-service`: `PaymentManagementController.getAllOrders()` and `UserManagementController.getAllUsers()` return `List<>` directly, not `ResponseEntity<List<>>`. No HTTP status code control.

#### Non-RESTful URLs
- `user-service` `EmailController` is at `/api/send-form-receipt` and `/api/send-form-notification` — action-based, not resource-based.

#### Key File Locations
- All controllers: `{service}-service/src/main/java/com/cyberlearnix/{service}/controller/`
- Gateway routes: `gateway-service/src/main/resources/application.yml`
- Only global exception handler: `enrollment-service/.../exception/GlobalExceptionHandler.java`

## Learnings

### Code Correctness Fixes — 2026-05-13

#### FIX 1: GlobalExceptionHandler — 8 services created
- Template from `enrollment-service/src/main/java/com/cyberlearnix/enrollment/exception/GlobalExceptionHandler.java`
- Covers: MethodArgumentNotValidException→400 (with fieldErrors), EntityNotFoundException/NoSuchElementException→404, AccessDeniedException→403, Exception→500
- Created in:
  - `admin-service/src/main/java/com/cyberlearnix/admin/exception/GlobalExceptionHandler.java`
  - `cms-service/src/main/java/com/cyberlearnix/cms/exception/GlobalExceptionHandler.java`
  - `course-service/src/main/java/com/cyberlearnix/course/exception/GlobalExceptionHandler.java`
  - `form-service/src/main/java/com/cyberlearnix/form/exception/GlobalExceptionHandler.java`
  - `instructor-service/src/main/java/com/cyberlearnix/instructor/exception/GlobalExceptionHandler.java`
  - `notification-service/src/main/java/com/cyberlearnix/notification/exception/GlobalExceptionHandler.java`
  - `shop-service/src/main/java/com/cyberlearnix/shop/exception/GlobalExceptionHandler.java`
  - `user-service/src/main/java/com/cyberlearnix/user/exception/GlobalExceptionHandler.java`

#### FIX 2: HTTP 201 on creation endpoints
- `course-service`: ExamManagementController.createExam(), CertificateController.issueCertificate(), ContentReviewController.submitForReview(), AssignmentController.createAssignment(), UpdateController.createUpdate() — all changed to 201
- `form-service`: FormController.createForm() return type changed from bare FormResponseDTO to ResponseEntity<FormResponseDTO> with 201
- `user-service`: CareerController.createJob(), ChatbotController.createResponse(), MenuController.createMenu(), TeamCollaborationController.createTeam(), ContactSubmissionController.createSubmission() — all changed to 201
- `enrollment-service`: FormController.deleteConfig() changed from 200 to 204 NoContent
- NOTE: CourseController.createCourse() and CourseManagementController.createCourse() already returned 201 — no change needed

#### FIX 3: @Valid added to request bodies
- `course-service`: CourseController.createCourse() and replaceCourse() — added `@Valid` + `import jakarta.validation.Valid`
- `course-service`: CourseManagementController.createCourse() — added `@Valid` (import was already present)
- `course-service`: ContentReviewController.submitForReview() — added `@Valid` + import
- `cms-service`: PageController.createPage() and updatePage() — added `@Valid` + import
- `admin-service`: AdminController.login() was using `Map<String, String>`. Created `AdminLoginRequest` DTO (`com.cyberlearnix.admin.dto.AdminLoginRequest`) with email/password + @NotBlank/@Email constraints. Updated login() to use it with `@Valid`.

#### FIX 4: form-service dead controller method
- `FormController.getAdminForm()` had `@PreAuthorize("hasRole('ADMIN')")` but no HTTP mapping — @PreAuthorize was dead (Spring AOP doesn't intercept direct same-class calls)
- Fixed: removed dead method, inlined logic into `getForm`, applied `@PreAuthorize("hasRole('ADMIN') or #token != null")`
- This means: admin always has access; public token-based access permitted; unauthenticated no-token access blocked

#### FIX 5: ContactSubmissionController raw Thread
- Removed `new Thread(...)` in `ContactSubmissionController.createSubmission()`
- Added `@Async` to `EmailNotificationService.sendAdminInquiryNotification()` — method is now truly async via Spring's task executor
- Added `@EnableAsync` to `UserServiceApplication.java`
- Key files: `user-service/.../controller/ContactSubmissionController.java`, `user-service/.../service/EmailNotificationService.java`, `user-service/.../UserServiceApplication.java`

#### FIX 6: course-service BannerController, PartnerController, PromoController X-User-Role header checks
- `BannerController`: POST/PUT/DELETE — removed `@RequestHeader("X-User-Role") String userRole` + manual if-check; added `@PreAuthorize("hasRole('ADMIN')")` + import
- `PartnerController`: POST/PUT/DELETE had NO auth at all — added `@PreAuthorize("hasRole('ADMIN')")` + import
- `PromoController`: POST/PUT/DELETE — same pattern as BannerController

#### FIX 7: user-service SiteSettingsController X-User-Role check
- POST and DELETE — removed `@RequestHeader("X-User-Role") String userRole` + manual if-check; added `@PreAuthorize("hasRole('ADMIN')")` + import
- File: `user-service/src/main/java/com/cyberlearnix/user/controller/SiteSettingsController.java`

#### Key Patterns Learned
- `@EnableMethodSecurity` was already present in: course-service, user-service, admin-service, form-service (via @EnableMethodSecurity on SecurityConfig)
- `@PreAuthorize` with parameter binding (`#token != null`) works for optional request params — useful when an endpoint has dual public/secured paths
- `@Async` requires `@EnableAsync` on the Spring Boot main class — always check before adding @Async methods
- When adding HttpStatus.CREATED to controllers that don't import HttpStatus yet, must add the import explicitly

## attendance-service — Added 2026-05-23

### New Service
- Port: 8092, DB: `cyberlearnix_attendance`
- Package root: `com.cyberlearnix.attendance`
- Main class: `AttendanceServiceApplication` (no `@EnableFeignClients` — Zoho client uses RestTemplate)
- 7 entities: `Meeting`, `MeetingSession`, `FinalAttendance`, `AttendanceLog`, `AttendanceOverride`, `CertificateEligibility`, `AuditLog`
- 7 repos, 8 DTOs, 9 services, 5 controllers

### Bugs Fixed During Code Review
1. **`AttendanceEngineService` missing import** — `com.cyberlearnix.attendance.dto.AttendanceOverrideRequest` was not imported; entity wildcard doesn't cover dto package. Always import DTOs explicitly.
2. **Invalid JPQL `JOIN ON`** — `FinalAttendance.findByCourseAndStudent` used `JOIN Meeting m ON fa.meetingId = m.id` which is invalid JPQL (only allowed for mapped relationships). Fixed with subquery: `WHERE fa.meetingId IN (SELECT m.id FROM Meeting m WHERE m.courseId = :courseId)`.
3. **`@EnableFeignClients` pointing at non-existent package** — Removed since no `@FeignClient` interfaces exist; ZohoMeetingApiClient uses RestTemplate.

### Conventions for This Service
- All attendance calculation in `AttendanceEngineService` — never duplicate in controllers
- `AttendanceOverrideRequest.action` is `@NotBlank` — must always be set before sending
- Webhook endpoint `/api/attendance/webhooks/zoho` is public (no JWT) — validated by `X-Zoho-Meeting-Token` header
- Scheduler tasks: finalize every 5min, disconnect detection every 2min, stale meetings every 1hr

## lab-service — Added 2026-05-30

### New Service
- Port: 8090, DB: `lab_db`
- Package root: `com.cyberlearnix.lab`
- Main class: `LabServiceApplication` with `@EnableScheduling`
- 2 entities: `LabTemplate`, `LabAssignment` (local, NOT in shared-lib)
- 2 repos: `LabTemplateRepository`, `LabAssignmentRepository`
- 2 services: `DockerClientService` (docker-java API), `LabService` (business logic)
- 1 controller: `LabController` at `/api/labs/**`
- 1 WebSocket handler: `LabTerminalWebSocketHandler` at `/labs/terminal/{assignmentId}`
- 3 configs: `DockerConfig`, `SecurityConfig`, `WebSocketConfig`
- 1 `GlobalExceptionHandler` (covers 400/404/409/403/500)

### Key Patterns
- `assignLab()` saves DB record FIRST to get the id, then names container `cyberlearnix-lab-{studentId}-{id}` — avoids id-unknown naming problem
- Docker CPU limits use CFS bandwidth: `cpuQuota = cpu * 100_000`, `cpuPeriod = 100_000`
- WebSocket terminal: `ExecStartResultCallback` streams stdout frames to browser; `PipedOutputStream` feeds stdin from browser messages; each session gets a daemon thread
- Idle cleanup `@Scheduled(fixedDelay=300_000)` stops (not removes) labs idle > `lab.defaults.idle-timeout-minutes`
- `AssignmentStatus` enum: PENDING → PROVISIONING → RUNNING → PAUSED / TERMINATED

### Pre-deploy Requirements
- Docker network must exist: `docker network create cyberlearnix-labs-network`
- `/var/run/docker.sock` must be mounted into lab-service container
- Gateway needs routes: `/api/labs/**` → `lab-service:8090` AND `/labs/terminal/**` (WebSocket upgrade)
- `lab_db` database must be created in PostgreSQL
- Dependencies: `com.github.docker-java:docker-java:3.3.4` + `docker-java-transport-httpclient5:3.3.4`

### Learnings
- `docker-java` `ExecStartResultCallback.onNext(Frame)` delivers stdout/stderr as `Frame` objects; use `frame.getPayload()` to get the bytes
- Spring WebSocket path variables (`/labs/terminal/{assignmentId}`) are NOT extracted automatically — must parse `session.getUri().getPath()` manually
- `@EnableScheduling` must be on the main application class or a `@Configuration` class; missing it silently skips all `@Scheduled` methods
### Role-Switch Permission Expansion — 2026-06-01
- **What changed:** Replaced the flat `canSwitch` boolean in `AuthService.switchRole()` with a declarative permission matrix (`allowedSwitches` map).
- **New permissions added:** `teacher` → `student`, `institute` → `teacher` or `student`. `admin` now also explicitly allows `institute`.
- **Why:** Teacher needed to preview the student portal; institute admins needed to preview both teacher and student portals for QA and support workflows.
- **Controller verified:** `AuthController.POST /switch-role` correctly calls `authService.switchRole(userId, targetRole)` and returns `ResponseEntity.ok(result)` (HTTP 200 with `{token, role}` body). On exception it returns HTTP 403. No changes needed to the controller.
- **Java note:** Used fully-qualified `java.util.Map.of(...)` and `java.util.List.of(...)` inline — `Map` was already in scope from the return statement import.

### Enrollment Service — Form Response Count Endpoint — 2026-06-01

- **New endpoint:** `GET /api/enrollments/forms/response-counts` → returns `Map<String, Long>` (formId → active response count)
- **Repository method added:** `countByFormIdAndDeletedAtIsNull(String formId)` in `EnrollmentFormResponseRepository` (Spring Data derived query, zero SQL needed)
- **Service method added:** `EnrollmentService.getFormResponseCounts()` — iterates `configRepository.findByDeletedAtIsNull()` and builds the map via the new count query
- **Key pattern:** `EnrollmentFormConfig.fields` is stored as `String` JSONB (via `RawJsonDeserializer`). HTTP response emits it as a raw JSON string literal (e.g. `"fields": "[{...}]"`) — NOT as an inline array. Frontend must parse it; backend does NOT need to change.
- **`responseRepository` was already injected** in `EnrollmentService` via constructor — `import com.cyberlearnix.shared.repository.enrollment.*` wildcard covers `EnrollmentFormResponseRepository`.
- BUILD SUCCESSFUL (only deprecation warning from PaymentService, unrelated).

### [2026-06-12] Form Request DTO — Lombok Builder Default Deserialization Fix
- **Bug Fixed:** When raw JSON was deserialized into `FormRequestDTO` (e.g. during form creation/update), Lombok `@Builder.Default private boolean isActive = true;` was being bypassed. Jackson uses the default constructor compiled with `@NoArgsConstructor`, which doesn't initialize `@Builder.Default` fields to their defined default values. Instead, they default to JVM values (e.g., `false` for `boolean`). This caused newly created public forms to be inactive by default, triggering form loading failure (HTTP 400 RuntimeException: Form is currently not accepting responses).
- **Fix:** Removed lombok's `@NoArgsConstructor` from `FormRequestDTO.java` and replaced it with a manual no-argument constructor that explicitly sets `this.isActive = true;`.
- **Validation:** Running `./gradlew :form-service:build` and `:form-service:test` compiles successfully and all tests pass cleanly.
