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
