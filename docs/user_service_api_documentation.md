# Cyberlearnix User-Service: FINAL EXHAUSTIVE API CATALOG

This document is the "source of truth" for all available APIs in the `user-service` (Port 8081).

---

## 1. Authentication & Security (Controller: `AuthController`)
- `POST /api/auth/login`: Standard email/password login.
- `POST /api/auth/register`: (Admin Only) Create new student/teacher accounts.
- `POST /api/auth/logout`: Blacklist access token and remove refresh token.
- `POST /api/auth/refresh-token`: Exchange refresh token for new access token.
- `POST /api/auth/request-otp`: Request OTP for password reset.
- `POST /api/auth/verify-otp`: Verify OTP to get reset token.
- `POST /api/auth/reset-password`: Set new password with reset token.
- `POST /api/auth/request-login-otp`: Request OTP for passwordless login.
- `POST /api/auth/verify-otp-login`: Authenticate via OTP.
- `POST /api/auth/change-password`: Update password while logged in.
- `POST /api/auth/switch-role`: Toggle between student/teacher roles.

## 2. Identity (Controller: `UserController`)
- `GET /api/users/all`: (Admin Only) List all system users.

## 3. Careers (Controller: `CareerController`)
- `GET /api/careers`: List jobs (params: `status`, `type`).
- `POST /api/careers`: (Admin) Add job.
- `PUT /api/careers/{id}`: (Admin) Update job.
- `DELETE /api/careers/{id}`: (Admin) Remove job.

## 4. Support & Chat (Controllers: `ChatbotController`, `ContactSubmissionController`)
- `GET /api/chatbot`: List chatbot intents.
- `POST /api/chatbot`: (Admin) Add intent.
- `PUT /api/chatbot/{id}`: (Admin) Update intent.
- `DELETE /api/chatbot/{id}`: (Admin) Remove intent.
- `POST /api/chatbot/{id}/train`: Record intent usage/training.
- `POST /api/contact-submissions`: Public inquiry submission.
- `GET /api/contact-submissions`: (Admin) List inquiries.
- `PATCH /api/contact-submissions/{id}`: (Admin) Update status/notes.
- `PATCH /api/contact-submissions/{id}/notes`: (Admin) Update admin specific notes.
- `DELETE /api/contact-submissions/{id}`: (Admin) Soft-delete submission.

## 5. Communications (Controller: `EmailController`)
- `POST /api/send-form-receipt`: Send "We got your form" to user.
- `POST /api/send-form-notification`: Send "New form submitted" to admin.
- `POST /api/send-reply`: (Admin) Email a user from the helpdesk.
- `POST /api/send-email`: Send general contact form inquiry to admin.

## 6. Site UI (Controller: `MenuController`, `SiteSettingsController`)
- `GET /api/site-settings`: Fetch config (params: `group`).
- `GET /api/site-settings/{key}`: Fetch specific config.
- `POST /api/site-settings`: (Admin) Update config (Requires `X-User-Role` header).
- `DELETE /api/site-settings/{key}`: (Admin) Remove config.
- `GET /api/menus`: Fetch navigation (params: `location`).
- `POST /api/menus`: (Admin) Add menu item.
- `PUT /api/menus/{id}`: (Admin) Full update.
- `DELETE /api/menus/{id}`: (Admin) Remove menu item.
- `PATCH /api/menus/{id}/toggle`: Toggle active/inactive status.
- `PATCH /api/menus/{id}/reorder`: Set display order.

## 7. Teams & Activity (Controller: `TeamCollaborationController`, `ActivityLogController`)
- `GET /api/teams`: List teams (params: `courseId`).
- `GET /api/teams/{id}`: Get team details.
- `POST /api/teams`: Create team (Automated access code).
- `PUT /api/teams/{id}`: Update team details.
- `DELETE /api/teams/{id}`: Disband team.
- `POST /api/teams/{id}/members`: Add student to team.
- `GET /api/activity/logs/{userId}`: View user audit log.
- `POST /api/activity/logs`: Internal event logging.
