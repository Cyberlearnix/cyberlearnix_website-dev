---
updated_at: 2026-05-30T00:00:00.000Z
focus_area: Lab Service — student container management feature
active_issues:
  - lab-service scaffold complete; port alignment needed (8093 per ADR-006)
  - SEC-005 security design pending Srini review
  - docker-socket-proxy not yet in docker-compose (Rohit action item)
---

# What We're Focused On

**Project:** Cyberlearnix — an e-learning platform built as Java Spring Boot microservices.

**Active Services:** admin, cms, course, enrollment, form, gateway, instructor, notification, shop, user, attendance, lab + shared-lib (13 services total).

**Current Sprint:** Lab Service — student Linux container management feature.

**Active work:**
- `lab-service` scaffolded by Shiva (Spring Boot, port 8093, Docker Java SDK, WebSocket terminal)
- ADR-006/007/008 authored by Srini (architecture, terminal, lifecycle)
- Security design (SEC-005) authored by Sandeep — CRITICAL risk review pending Srini sign-off
- Infrastructure resource plan authored by Rohit — docker-socket-proxy action item outstanding
- Gateway routes for `/api/labs/**` and `/labs/terminal/**` added
- Session log written: `.squad/log/2026-05-30-lab-service-planning.md`

**Next gate:** Srini reviews SEC-005 → Rohit adds docker-socket-proxy to compose → Sneha writes integration tests → feature ready for deploy.
