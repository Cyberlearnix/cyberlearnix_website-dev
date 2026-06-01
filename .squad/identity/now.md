---
updated_at: 2026-06-01T00:00:00.000Z
focus_area: Cyberlearnix — Admin Portal UI + Enrollment Service
active_issues: []
---

# What We're Focused On

**Project:** Cyberlearnix — an e-learning platform built as Java Spring Boot microservices.

**Active Services:** admin, cms, course, enrollment, form, gateway, instructor, notification, shop, user + shared-lib.

**Last Sprint (2026-06-01):** Admin portal bug-fix + enrollment forms fully dynamic from DB.
- AdminNavbar scroll timing fixed (ResizeObserver + RAF + scroll-to-active)
- CourseManagement "0 Enrolled" → real count from `/api/admin/reports/courses`
- Enrollment Forms FormsTab: fields count bug fixed (`parseFieldCount` helper), response counts added via new `GET /api/enrollments/forms/response-counts` endpoint
- All enrollment form CRUD confirmed saving/loading from PostgreSQL `enrollment_forms_config` table

**Team is ready for next task.**
