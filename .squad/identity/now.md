---
updated_at: 2026-06-06T00:00:00.000Z
focus_area: Cyberlearnix — Two-Environment CI/CD Pipeline
active_issues:
  - "GitHub Secret DEV_BASIC_AUTH_PASSWORD not yet set — dev basic auth non-functional until resolved"
---

# What We're Focused On

**Project:** Cyberlearnix — an e-learning platform built as Java Spring Boot microservices.

**Active Services:** admin, cms, course, enrollment, form, gateway, instructor, notification, shop, user + shared-lib.

**Last Sprint (2026-06-06):** Two-environment CI/CD pipeline is live.
- Created `.github/workflows/deploy-production.yml` — triggered on push to `production`; deploys to `www.cyberlearnix.com` (no basic auth)
- Updated `.github/workflows/ci-cd.yml` — deploy job renamed to `deploy-dev`; basic auth setup added for `dev.cyberlearnix.com`
- Pipeline now correctly separates dev (main branch) from production (production branch)

**Pending:** GitHub Secret `DEV_BASIC_AUTH_PASSWORD` must be added to repo settings before dev basic auth is active.

## Branch Strategy (ADR-006)
| Branch | Domain | Auth | K8s Namespace |
|--------|--------|------|---------------|
| `main` | `dev.cyberlearnix.com` | Basic Auth (htpasswd) | `cyberlearnix` |
| `production` | `www.cyberlearnix.com` | None (live) | `cyberlearnix-production` |
