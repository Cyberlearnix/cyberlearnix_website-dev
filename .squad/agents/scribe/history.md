# Project Context

- **Project:** cyberlearnix_website-dev
- **Created:** 2026-04-20

## Core Context

Agent Scribe initialized and ready for work.

## Recent Updates

📌 Team initialized on 2026-04-20

## Learnings

Initial setup complete.

### [2026-06-12] Session Sync — Lombok Builder Deserialization Fix
- **Orchestration Log:** Logged Agent Shiva's sync task for resolving the form active default bug under `.squad/orchestration-log/2026-06-12T120000Z-shiva.md`.
- **Session Log:** Documented the full context of the Lombok default constructor Jackson deserialization fix under `.squad/log/2026-06-12T120000Z-form-active-lombok-fix.md`.
- **Decision Inbox:** Merged three proposed decisions (`rohit-adr007-two-env-pipeline.md`, `rohit-adr008-gitops-argocd.md`, and `shiva-form-active-lombok-fix.md`) from `.squad/decisions/inbox/` into `.squad/decisions.md` as accepted decisions and successfully lowered the inbox workload. All merged files have been deleted.

### 2026-05-13 — Full Data & DB Verification Across 10 Services

**Critical issues discovered:**

1. **shop-service `PUT /api/shop` has zero authorization** — hardcoded comment "Auth check for admin role should be implemented here" but never implemented. Any authenticated user can modify shop settings.

2. **AdminSeeder resets the admin password on every app restart** — The `else` branch in `AdminSeeder.run()` unconditionally re-encodes `"Shivam$179"` even if admin already exists. Production password changes are silently overwritten on each deploy.

3. **Notification broadcast unprotected** — `POST /api/notifications?action=broadcast` has no `@PreAuthorize`. Any user with a valid JWT can trigger bulk email broadcasts.

4. **`secure(false)` on all auth cookies** — Three `ResponseCookie.secure(false)` calls in `AuthController` with TODO comments. Production HTTPS deployments are affected.

5. **`User.passwordHash` has no `@JsonIgnore`** — Entity is not directly returned in current code paths (DTOs are used), but the field is unprotected at entity level — one careless refactor away from exposure.

6. **No Flyway migrations anywhere** — All 10 services rely entirely on `spring.jpa.hibernate.ddl-auto`. No version-controlled schema history.

7. **`@Valid` only present in 6 of 61 endpoints** — Systemic gap. Most services validate manually or not at all.

8. **CMS accepts raw JPA entities in request body** — `PageSection` and `PageComponent` entities (with JPA relationship fields) are bound directly from HTTP request bodies.

9. **Enrollment form accepts raw `EnrollmentFormConfig` entity** — No DTO layer for form create/update.

10. **Two N+1 query risks in shared-lib**: `QuizQuestion.options` is `FetchType.EAGER`, `CourseModule.course` is `FetchType.EAGER`.

11. **Enrollment payment finalization is non-atomic** — PaymentService and EnrollmentService use separate `@Transactional` methods. A crash between payment confirmed and enrollment created leaves the record in a broken state.

12. **Duplicate `SiteSettingRepository`** in admin-service (local) AND shared-lib — divergence risk.

**Positive findings:**
- AuthService has proper `@Transactional` on all write operations
- PaymentService and EnrollmentService have good `@Transactional` discipline  
- Gateway correctly strips X-User-Id / X-User-Role to prevent injection
- Redis token blacklist check is implemented in gateway
- UserService.getAllUsers() properly maps through UserResponseDTO (no passwordHash leak)
- Pagination is implemented in CourseController GET
- CMS PageService has consistent `@Transactional` on all write operations
