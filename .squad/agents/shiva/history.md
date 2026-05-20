# Shiva — Backend Engineer History

## What I Know About This Project

### Service Structure
Each microservice under `{service}-service/src/main/` follows the standard Spring Boot layout.
Shared types are in `shared-lib/`. All services declare `implementation project(':shared-lib')`.

### Build
- Root build: `./gradlew build`
- Single service: `./gradlew :{name}-service:build`
- Compiled classes go to `build/classes/`, JARs to `build/libs/`

### First Session
- No features implemented yet via Squad
- Baseline: project already has controllers, entities, and service layers in place

## Learnings

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
