# Cyberlearnix — User Service: Complete API Reference

**Service:** `user-service`
**Port:** `8081`
**Base URL:** `http://localhost:8081`
**API Prefix:** `/api`
**Swagger UI:** `http://localhost:8081/swagger-ui/index.html`
**OpenAPI JSON:** `http://localhost:8081/api/auth/v3/api-docs`

---

## Authentication

All protected endpoints require a JWT Bearer token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

| Role Level | Description |
|---|---|
| `Public` | No token required |
| `Authenticated` | Any valid JWT token |
| `ADMIN` | JWT token with `role: admin` |
| `Header: X-User-Role: admin` | Custom header used by SiteSettings endpoints |

---

## Table of Contents

1. [AuthController — `/api/auth`](#1-authcontroller)
2. [UserController — `/api/users`](#2-usercontroller)
3. [TeamCollaborationController — `/api/teams`](#3-teamcollaborationcontroller)
4. [SiteSettingsController — `/api/site-settings`](#4-sitesettingscontroller)
5. [MenuController — `/api/menus`](#5-menucontroller)
6. [EmailController — `/api`](#6-emailcontroller)
7. [ContactSubmissionController — `/api/contact-submissions`](#7-contactsubmissioncontroller)
8. [ChatbotController — `/api/chatbot`](#8-chatbotcontroller)
9. [CareerController — `/api/careers`](#9-careercontroller)
10. [ActivityLogController — `/api/activity/logs`](#10-activitylogcontroller)

---

---

## 1. AuthController

**Base Path:** `/api/auth`

---

### 1.1 Login

**`POST /api/auth/login`**
**Auth:** Public

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "yourPassword123"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | Registered user email |
| `password` | `String` | Yes | User password |

**Response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
  "user": {
    "id": "abc123",
    "email": "user@example.com",
    "role": "student",
    "isFirstLogin": false
  }
}
```

| Field | Type | Description |
|---|---|---|
| `token` | `String` | JWT access token |
| `refreshToken` | `String` | Refresh token to get new access tokens |
| `user.id` | `String` | User's unique identifier |
| `user.email` | `String` | User's email |
| `user.role` | `String` | User's current role (`student`, `teacher`, `admin`) |
| `user.isFirstLogin` | `Boolean` | True if this is the user's first login |

**Error Responses:**

| Status | Meaning |
|---|---|
| `401 Unauthorized` | Invalid credentials |
| `423 Locked` | Account locked after 5 failed attempts |

---

### 1.2 Register (Admin Only)

**`POST /api/auth/register`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "email": "newuser@example.com",
  "password": "SecurePass@123",
  "role": "student"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | New user's email address |
| `password` | `String` | Yes | Initial password |
| `role` | `String` | Yes | Role to assign: `student`, `teacher`, `admin` |

**Response — 201 Created:**
```json
{
  "id": "xyz789",
  "email": "newuser@example.com",
  "role": "student"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Newly created user's ID |
| `email` | `String` | User's email |
| `role` | `String` | Assigned role |

**Error Responses:**

| Status | Meaning |
|---|---|
| `400 Bad Request` | Email already exists or invalid input |
| `403 Forbidden` | Caller is not ADMIN |

---

### 1.3 Request OTP (Password Reset)

**`POST /api/auth/request-otp`**
**Auth:** Public

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | Email to send OTP to |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "sessionId": "sess_abc123xyz"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | `Boolean` | Whether OTP was sent |
| `message` | `String` | Human-readable status message |
| `sessionId` | `String` | Session identifier required for OTP verification |

> **Note:** Rate-limited — maximum requests per 15 minutes.

---

### 1.4 Request Login OTP (Passwordless Login)

**`POST /api/auth/request-login-otp`**
**Auth:** Public

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | Email to send login OTP to |

**Response — 200 OK:**
```json
{
  "message": "OTP sent to your email",
  "sessionId": "sess_login456abc"
}
```

| Field | Type | Description |
|---|---|---|
| `message` | `String` | Status message |
| `sessionId` | `String` | Session identifier required for OTP login verification |

---

### 1.5 Verify OTP (Password Reset)

**`POST /api/auth/verify-otp`**
**Auth:** Public

**Request Body:**
```json
{
  "email": "user@example.com",
  "otp": "482910",
  "sessionId": "sess_abc123xyz"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | User's email |
| `otp` | `String` | Yes | One-time password received via email |
| `sessionId` | `String` | Yes | Session ID returned from request-otp |

**Response — 200 OK:**
```json
{
  "success": true,
  "resetToken": "rst_tok_eyJhbGci..."
}
```

| Field | Type | Description |
|---|---|---|
| `success` | `Boolean` | Whether OTP was valid |
| `resetToken` | `String` | Token to be used in the reset-password endpoint |

**Error Responses:**

| Status | Meaning |
|---|---|
| `400 Bad Request` | Invalid or expired OTP |

---

### 1.6 Verify OTP Login (Passwordless)

**`POST /api/auth/verify-otp-login`**
**Auth:** Public

**Request Body:**
```json
{
  "email": "user@example.com",
  "otp": "738201",
  "sessionId": "sess_login456abc"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | `String` | Yes | User's email |
| `otp` | `String` | Yes | One-time password received for login |
| `sessionId` | `String` | Yes | Session ID returned from request-login-otp |

**Response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "abc123",
    "email": "user@example.com",
    "role": "student",
    "isFirstLogin": false
  }
}
```

| Field | Type | Description |
|---|---|---|
| `token` | `String` | JWT access token |
| `user.id` | `String` | User ID |
| `user.email` | `String` | User email |
| `user.role` | `String` | User role |
| `user.isFirstLogin` | `Boolean` | First login flag |

---

### 1.7 Switch Role

**`POST /api/auth/switch-role`**
**Auth:** Authenticated (only users with `dual` role or `admin`)

**Request Body:**
```json
{
  "role": "teacher"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `role` | `String` | Yes | Target role to switch to (`student`, `teacher`, `admin`) |

**Response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "teacher"
}
```

| Field | Type | Description |
|---|---|---|
| `token` | `String` | New JWT token with updated role |
| `role` | `String` | The newly active role |

---

### 1.8 Logout

**`POST /api/auth/logout`**
**Auth:** Authenticated

**Request Headers:**
```
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `refreshToken` | `String` | Yes | The refresh token to invalidate |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

### 1.9 Refresh Token

**`POST /api/auth/refresh-token`**
**Auth:** Public

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `refreshToken` | `String` | Yes | Valid refresh token |

**Response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "bmV3UmVmcmVzaFRva2Vu..."
}
```

| Field | Type | Description |
|---|---|---|
| `token` | `String` | New JWT access token |
| `refreshToken` | `String` | New refresh token |

**Error Responses:**

| Status | Meaning |
|---|---|
| `401 Unauthorized` | Refresh token expired or invalid |

---

### 1.10 Reset Password

**`POST /api/auth/reset-password`**
**Auth:** Public

**Request Body:**
```json
{
  "token": "rst_tok_eyJhbGci...",
  "newPassword": "NewSecurePass@456",
  "confirmPassword": "NewSecurePass@456"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `token` | `String` | Yes | Reset token from verify-otp response |
| `newPassword` | `String` | Yes | New password |
| `confirmPassword` | `String` | Yes | Must match `newPassword` |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Password reset successfully"
}
```

**Error Responses:**

| Status | Meaning |
|---|---|
| `400 Bad Request` | Passwords don't match or token invalid/expired |

---

### 1.11 Change Password

**`POST /api/auth/change-password`**
**Auth:** Authenticated

**Request Headers:**
```
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "newPassword": "UpdatedPass@789",
  "confirmPassword": "UpdatedPass@789"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `newPassword` | `String` | Yes | New desired password |
| `confirmPassword` | `String` | Yes | Must match `newPassword` |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

---

---

## 2. UserController

**Base Path:** `/api/users`

---

### 2.1 Get All Users

**`GET /api/users/all`**
**Auth:** ADMIN

**Request Headers:**
```
Authorization: Bearer <admin_token>
```

**Request Parameters:** None

**Response — 200 OK:**
```json
[
  {
    "id": "abc123",
    "email": "user@example.com",
    "fullName": "John Doe",
    "phone": "+91-9876543210",
    "role": "student",
    "isActive": true,
    "createdAt": "2025-01-15T10:30:00",
    "lastLogin": "2026-04-01T09:15:00"
  }
]
```

| Field | Type | Description |
|---|---|---|
| `id` | `String` | User's unique ID |
| `email` | `String` | User's email address |
| `fullName` | `String` | User's full name |
| `phone` | `String` | User's phone number |
| `role` | `String` | User's role (`student`, `teacher`, `admin`) |
| `isActive` | `Boolean` | Whether account is active |
| `createdAt` | `LocalDateTime` | Account creation timestamp |
| `lastLogin` | `LocalDateTime` | Last successful login timestamp |

---

---

## 3. TeamCollaborationController

**Base Path:** `/api/teams`

**TeamCollaboration Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Team ID (auto-generated) |
| `name` | `String` | Team name |
| `description` | `String` | Team description |
| `teamLeaderId` | `Long` | User ID of team leader |
| `memberIds` | `Map<String, Object>` | JSONB — map of member IDs and roles |
| `courseId` | `Long` | Associated course ID |
| `maxMembers` | `Integer` | Max allowed members (default: 5) |
| `isPrivate` | `Boolean` | Whether team is private |
| `accessCode` | `String` | Auto-generated code if team is private |
| `sharedResources` | `Map<String, Object>` | JSONB — shared resource links/data |
| `isActive` | `Boolean` | Whether team is active |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

### 3.1 List Teams

**`GET /api/teams`**
**Auth:** Public

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `courseId` | `Long` | No | Filter teams by course ID |

**Response — 200 OK:**
```json
[
  {
    "id": 1,
    "name": "Team Alpha",
    "description": "Frontend development team",
    "teamLeaderId": 101,
    "memberIds": {"101": "leader", "102": "member"},
    "courseId": 5,
    "maxMembers": 5,
    "isPrivate": false,
    "accessCode": null,
    "sharedResources": {},
    "isActive": true,
    "createdAt": "2025-03-01T10:00:00",
    "updatedAt": "2025-03-10T14:00:00"
  }
]
```

---

### 3.2 Get Team by ID

**`GET /api/teams/{id}`**
**Auth:** Public

**Path Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | `Long` | Yes | Team ID |

**Response — 200 OK:** Single `TeamCollaboration` object (same structure as above)

**Error Responses:**

| Status | Meaning |
|---|---|
| `404 Not Found` | Team with given ID not found |

---

### 3.3 Create Team

**`POST /api/teams`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "name": "Team Beta",
  "description": "Backend development team",
  "teamLeaderId": 105,
  "courseId": 7,
  "maxMembers": 4,
  "isPrivate": true
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `String` | Yes | Team name |
| `description` | `String` | No | Team description |
| `teamLeaderId` | `Long` | Yes | User ID of team leader |
| `courseId` | `Long` | No | Associated course ID |
| `maxMembers` | `Integer` | No | Max members (default: 5) |
| `isPrivate` | `Boolean` | No | Set private (auto-generates `accessCode`) |

**Response — 200 OK:** Created `TeamCollaboration` object

---

### 3.4 Update Team

**`PUT /api/teams/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:** Same as Create Team

**Response — 200 OK:** Updated `TeamCollaboration` object

---

### 3.5 Delete Team

**`DELETE /api/teams/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Response — 200 OK**

---

### 3.6 Add Member to Team

**`POST /api/teams/{id}/members`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:**
```json
{
  "userId": 110,
  "role": 2
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | `Long` | Yes | User ID to add |
| `role` | `Long` | Yes | Role identifier for the member |

**Response — 200 OK:** Updated `TeamCollaboration` object

---

---

## 4. SiteSettingsController

**Base Path:** `/api/site-settings`

**SiteSetting Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Setting ID (auto-generated) |
| `settingKey` | `String` | Unique key identifier |
| `settingValue` | `String` | Setting value |
| `metadata` | `Map<String, Object>` | JSONB — additional metadata |
| `settingGroup` | `String` | Grouping label (e.g., `"branding"`, `"seo"`) |
| `isActive` | `Boolean` | Whether setting is active |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

### 4.1 Get All Site Settings

**`GET /api/site-settings`**
**Auth:** Public

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `group` | `String` | No | Filter by setting group name |

**Response — 200 OK:**
```json
[
  {
    "id": 1,
    "settingKey": "site_logo",
    "settingValue": "https://cdn.example.com/logo.png",
    "metadata": {"width": 200, "height": 60},
    "settingGroup": "branding",
    "isActive": true,
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-06-01T00:00:00"
  }
]
```

---

### 4.2 Get Site Setting by Key

**`GET /api/site-settings/{key}`**
**Auth:** Public

**Path Parameters:** `key: String`

**Response — 200 OK:** Single `SiteSetting` object

---

### 4.3 Create/Update Site Setting (Upsert)

**`POST /api/site-settings`**
**Auth:** Header `X-User-Role: admin`

**Request Headers:**
```
X-User-Role: admin
```

**Request Body:**
```json
{
  "settingKey": "contact_email",
  "settingValue": "support@cyberlearnix.com",
  "settingGroup": "contact",
  "isActive": true
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `settingKey` | `String` | Yes | Unique key (used for upsert lookup) |
| `settingValue` | `String` | Yes | The value to store |
| `settingGroup` | `String` | No | Grouping name |
| `metadata` | `Object` | No | Additional JSON metadata |
| `isActive` | `Boolean` | No | Active flag (default true) |

**Response — 200 OK:** Saved `SiteSetting` object

---

### 4.4 Delete Site Setting

**`DELETE /api/site-settings/{key}`**
**Auth:** Header `X-User-Role: admin`

**Request Headers:**
```
X-User-Role: admin
```

**Path Parameters:** `key: String`

**Response — 200 OK:**
```json
{
  "success": true
}
```

---

---

## 5. MenuController

**Base Path:** `/api/menus`

**MenuItem Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Menu item ID (auto-generated) |
| `label` | `String` | Display label |
| `url` | `String` | Link URL |
| `location` | `String` | Menu location (e.g., `"header"`, `"footer"`) |
| `displayOrder` | `Integer` | Sort order |
| `isActive` | `Boolean` | Whether item is visible |
| `icon` | `String` | Icon class or URL |
| `parentId` | `Long` | Parent menu item ID (for nested menus) |
| `openInNewTab` | `Boolean` | Whether to open link in new tab |
| `cssClass` | `String` | Custom CSS class |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

### 5.1 Get Menu Items

**`GET /api/menus`**
**Auth:** Public

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `location` | `String` | No | Filter by location (`header`, `footer`, etc.) |

**Response — 200 OK:**
```json
[
  {
    "id": 1,
    "label": "Home",
    "url": "/",
    "location": "header",
    "displayOrder": 1,
    "isActive": true,
    "icon": null,
    "parentId": null,
    "openInNewTab": false,
    "cssClass": "nav-home",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

> Only active items are returned, sorted by `displayOrder`.

---

### 5.2 Create Menu Item

**`POST /api/menus`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "label": "Courses",
  "url": "/courses",
  "location": "header",
  "displayOrder": 2,
  "isActive": true,
  "openInNewTab": false
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | `String` | Yes | Display text |
| `url` | `String` | Yes | Link destination |
| `location` | `String` | Yes | Placement location |
| `displayOrder` | `Integer` | No | Position in menu |
| `isActive` | `Boolean` | No | Visibility flag |
| `icon` | `String` | No | Icon identifier |
| `parentId` | `Long` | No | For sub-menus |
| `openInNewTab` | `Boolean` | No | Open in new tab |
| `cssClass` | `String` | No | CSS class |

**Response — 200 OK:** Created `MenuItem` object

---

### 5.3 Update Menu Item

**`PUT /api/menus/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:** Same as Create Menu Item

**Response — 200 OK:** Updated `MenuItem` object

---

### 5.4 Delete Menu Item

**`DELETE /api/menus/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Response — 200 OK**

---

### 5.5 Toggle Menu Item Active Status

**`PATCH /api/menus/{id}/toggle`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:** None

**Response — 200 OK:** `MenuItem` with flipped `isActive` value

---

### 5.6 Reorder Menu Item

**`PATCH /api/menus/{id}/reorder`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:**
```json
{
  "order": 3
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `order` | `Integer` | Yes | New display order position |

**Response — 200 OK:** Updated `MenuItem` object

---

---

## 6. EmailController

**Base Path:** `/api`

> All endpoints require authentication. `/api/send-reply` additionally requires ADMIN role.
> Email delivery uses **Resend API** as primary, falls back to **SMTP (Gmail/JavaMailSender)**.

---

### 6.1 Send Form Receipt to User

**`POST /api/send-form-receipt`**
**Auth:** Authenticated

**Request Body:**
```json
{
  "recipientEmail": "user@example.com",
  "formTitle": "Course Enrollment Form",
  "responses": [
    { "question": "Full Name", "answer": "John Doe" },
    { "question": "Course", "answer": "Python Basics" }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `recipientEmail` | `String` | Yes | Email address to send receipt to |
| `formTitle` | `String` | Yes | Title of the submitted form |
| `responses` | `List<Object>` | Yes | List of `{question, answer}` pairs |

**Response — 200 OK:**
```json
{
  "message": "Form receipt sent successfully"
}
```

---

### 6.2 Send Form Notification to Admin

**`POST /api/send-form-notification`**
**Auth:** Authenticated

**Request Body:**
```json
{
  "respondentEmail": "user@example.com",
  "formTitle": "Contact Us",
  "answers": [
    { "question": "Name", "answer": "Jane Smith" },
    { "question": "Message", "answer": "I need help with..." }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `respondentEmail` | `String` | Yes | Email of the person who submitted the form |
| `formTitle` | `String` | Yes | Form title |
| `answers` | `List<Object>` | Yes | List of `{question, answer}` pairs |

**Response — 200 OK:**
```json
{
  "message": "Admin notification sent successfully"
}
```

---

### 6.3 Send Reply Email (Admin Helpdesk)

**`POST /api/send-reply`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "to": "user@example.com",
  "subject": "Re: Your inquiry",
  "message": "Thank you for reaching out. We have resolved your query..."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | `String` | Yes | Recipient email address |
| `subject` | `String` | Yes | Email subject line |
| `message` | `String` | Yes | Email body content |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Email sent successfully"
}
```

---

### 6.4 Send Contact Form Email

**`POST /api/send-email`**
**Auth:** Authenticated

**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "+91-9876543210",
  "message": "I am interested in your courses..."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `String` | Yes | Sender's name |
| `email` | `String` | Yes | Sender's email |
| `phone` | `String` | No | Sender's phone number |
| `message` | `String` | Yes | Message content |

**Response — 200 OK:**
```json
{
  "success": true,
  "message": "Message sent to admin"
}
```

---

---

## 7. ContactSubmissionController

**Base Path:** `/api/contact-submissions`

**ContactSubmission Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Submission ID (auto-generated) |
| `name` | `String` | Submitter's name |
| `email` | `String` | Submitter's email |
| `phone` | `String` | Submitter's phone |
| `message` | `String` | Submission message |
| `status` | `String` | Status: `"unread"` or `"read"` |
| `adminNotes` | `String` | Internal notes added by admin |
| `createdAt` | `LocalDateTime` | Submission timestamp |
| `deletedAt` | `LocalDateTime` | Soft-delete timestamp (null if active) |

---

### 7.1 Get All Contact Submissions

**`GET /api/contact-submissions`**
**Auth:** ADMIN

**Request Parameters:** None

**Response — 200 OK:**
```json
[
  {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+91-9876543210",
    "message": "I want to enroll in Python course",
    "status": "unread",
    "adminNotes": null,
    "createdAt": "2026-03-20T10:00:00",
    "deletedAt": null
  }
]
```

> Soft-deleted entries (where `deletedAt` is not null) are excluded.

---

### 7.2 Create Contact Submission

**`POST /api/contact-submissions`**
**Auth:** Public

**Request Body:**
```json
{
  "name": "Jane Smith",
  "email": "jane@example.com",
  "phone": "+91-8765432109",
  "message": "Please contact me for course details"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | `String` | Yes | Submitter's full name |
| `email` | `String` | Yes | Submitter's email |
| `phone` | `String` | No | Submitter's phone number |
| `message` | `String` | Yes | Message body |

**Response — 201 Created:** Created `ContactSubmission` object

> **Note:** An admin email notification is sent asynchronously on every submission.

---

### 7.3 Update Submission Status/Notes

**`PATCH /api/contact-submissions/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:**
```json
{
  "status": "read",
  "adminNotes": "Contacted via phone, resolved"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `status` | `String` | No | New status (`"read"` or `"unread"`) |
| `adminNotes` | `String` | No | Internal notes (also accepts `admin_notes`) |

**Response — 200 OK:** Updated `ContactSubmission` object

---

### 7.4 Update Admin Notes Only

**`PATCH /api/contact-submissions/{id}/notes`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:**
```json
{
  "admin_notes": "Follow up scheduled for April 10"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `admin_notes` | `String` | Yes | Notes to save |

**Response — 200 OK:** Updated `ContactSubmission` object

---

### 7.5 Delete Contact Submission (Soft Delete)

**`DELETE /api/contact-submissions/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Response — 200 OK**

> Sets `deletedAt` timestamp. Record is NOT permanently removed from the database.

---

---

## 8. ChatbotController

**Base Path:** `/api/chatbot`

**ChatbotResponse Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Chatbot response ID (auto-generated) |
| `intent` | `String` | Intent name/identifier |
| `response` | `String` | Bot reply text |
| `trainingPhrases` | `Map<String, Object>` | JSONB — phrases that trigger this intent |
| `category` | `String` | Category grouping |
| `confidenceThreshold` | `Double` | Minimum confidence score to trigger |
| `requiresFollowup` | `Boolean` | Whether a follow-up question is needed |
| `followupQuestions` | `String` | Follow-up question text |
| `isActive` | `Boolean` | Whether this response is in use |
| `usageCount` | `Integer` | Times this response was triggered |
| `lastUsedAt` | `LocalDateTime` | Last trigger timestamp |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

### 8.1 Get Chatbot Responses

**`GET /api/chatbot`**
**Auth:** Public

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `category` | `String` | No | Filter by category |

**Response — 200 OK:** `List<ChatbotResponse>`

---

### 8.2 Create Chatbot Response

**`POST /api/chatbot`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "intent": "course_inquiry",
  "response": "We offer a wide range of courses. Please visit /courses to explore.",
  "category": "courses",
  "confidenceThreshold": 0.75,
  "requiresFollowup": false,
  "isActive": true,
  "trainingPhrases": {
    "phrases": ["tell me about courses", "what courses do you offer", "course list"]
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `intent` | `String` | Yes | Intent identifier |
| `response` | `String` | Yes | Bot reply text |
| `category` | `String` | No | Category |
| `confidenceThreshold` | `Double` | No | Confidence threshold |
| `requiresFollowup` | `Boolean` | No | Followup flag |
| `followupQuestions` | `String` | No | Followup text |
| `isActive` | `Boolean` | No | Active flag |
| `trainingPhrases` | `Object` | No | Training phrases JSONB |

**Response — 200 OK:** Created `ChatbotResponse` object

---

### 8.3 Update Chatbot Response

**`PUT /api/chatbot/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:** Same as Create Chatbot Response

**Response — 200 OK:** Updated `ChatbotResponse` object

---

### 8.4 Delete Chatbot Response

**`DELETE /api/chatbot/{id}`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Response — 200 OK**

---

### 8.5 Train Chatbot Response

**`POST /api/chatbot/{id}/train`**
**Auth:** ADMIN

**Path Parameters:** `id: Long`

**Request Body:**
```json
{
  "new_phrase": "do you have any courses",
  "language": "en"
}
```

> Accepts any `Map<String, String>` of training data key-value pairs.

**Response — 200 OK:** `ChatbotResponse` object with incremented `usageCount`

---

---

## 9. CareerController

**Base Path:** `/api/careers`

**JobOpening Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Job opening ID (auto-generated) |
| `title` | `String` | Job title |
| `type` | `String` | Employment type (e.g., `"full-time"`, `"internship"`) |
| `department` | `String` | Department name |
| `location` | `String` | Work location |
| `description` | `String` | Job description |
| `requirements` | `String` | Required qualifications |
| `responsibilities` | `String` | Job responsibilities |
| `status` | `String` | Status: default `"open"` |
| `formId` | `String` | Associated application form ID |
| `createdAt` | `LocalDateTime` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | Last update timestamp |

---

### 9.1 Get Job Openings

**`GET /api/careers`**
**Auth:** Public

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `String` | No | Filter by status (e.g., `"open"`, `"closed"`) |
| `type` | `String` | No | Filter by job type |

**Response — 200 OK:** `List<JobOpening>`

---

### 9.2 Create Job Opening

**`POST /api/careers`**
**Auth:** Authenticated

**Request Body:**
```json
{
  "title": "Frontend Developer Intern",
  "type": "internship",
  "department": "Engineering",
  "location": "Remote",
  "description": "Work on our React-based LMS platform",
  "requirements": "React, JavaScript, HTML/CSS",
  "responsibilities": "Build UI components, fix bugs",
  "status": "open",
  "formId": "form_abc123"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | `String` | Yes | Job title |
| `type` | `String` | Yes | Employment type |
| `department` | `String` | No | Department |
| `location` | `String` | No | Work location |
| `description` | `String` | Yes | Job description |
| `requirements` | `String` | No | Skills/qualifications |
| `responsibilities` | `String` | No | Duties |
| `status` | `String` | No | Default: `"open"` |
| `formId` | `String` | No | Application form ID |

**Response — 200 OK:** Created `JobOpening` object

> ⚠️ **Security Note:** Write operations require a valid JWT but have **no ADMIN role enforcement**. Any authenticated user can create/update/delete job postings.

---

### 9.3 Update Job Opening

**`PUT /api/careers/{id}`**
**Auth:** Authenticated

**Path Parameters:** `id: UUID`

**Request Body:** Same as Create Job Opening

**Response — 200 OK:** Updated `JobOpening` object

---

### 9.4 Delete Job Opening

**`DELETE /api/careers/{id}`**
**Auth:** Authenticated

**Path Parameters:** `id: UUID`

**Response — 200 OK**

---

---

## 10. ActivityLogController

**Base Path:** `/api/activity/logs`

**ActivityLog Object:**

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Log entry ID (auto-generated) |
| `userId` | `String` | ID of the user who performed the action |
| `eventType` | `String` | Type of event (e.g., `"LOGIN"`, `"PASSWORD_CHANGE"`) |
| `description` | `String` | Human-readable event description |
| `metadata` | `Map<String, Object>` | JSONB — additional contextual data |
| `createdAt` | `LocalDateTime` | Event timestamp |

---

### 10.1 Get Activity Logs by User

**`GET /api/activity/logs/{userId}`**
**Auth:** ADMIN

**Path Parameters:** `userId: String`

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `limit` | `int` | No | Max number of logs to return (default: 10) |

**Response — 200 OK:** `List<ActivityLog>` ordered by `createdAt` descending

```json
[
  {
    "id": 42,
    "userId": "abc123",
    "eventType": "LOGIN",
    "description": "User logged in successfully",
    "metadata": { "ip": "192.168.1.1", "device": "Chrome/Windows" },
    "createdAt": "2026-04-04T09:00:00"
  }
]
```

---

### 10.2 Create Activity Log

**`POST /api/activity/logs`**
**Auth:** ADMIN

**Request Body:**
```json
{
  "userId": "abc123",
  "eventType": "PROFILE_UPDATE",
  "description": "User updated their profile information",
  "metadata": {
    "fieldsChanged": ["fullName", "phone"]
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | `String` | Yes | User's ID |
| `eventType` | `String` | Yes | Event category/type |
| `description` | `String` | No | Readable description |
| `metadata` | `Object` | No | Extra data in JSONB format |

**Response — 200 OK:** Created `ActivityLog` object

---

---

## Summary — All Endpoints

| # | Controller | Method | Endpoint | Auth |
|---|---|---|---|---|
| 1 | Auth | POST | `/api/auth/login` | Public |
| 2 | Auth | POST | `/api/auth/register` | ADMIN |
| 3 | Auth | POST | `/api/auth/request-otp` | Public |
| 4 | Auth | POST | `/api/auth/request-login-otp` | Public |
| 5 | Auth | POST | `/api/auth/verify-otp` | Public |
| 6 | Auth | POST | `/api/auth/verify-otp-login` | Public |
| 7 | Auth | POST | `/api/auth/switch-role` | Authenticated |
| 8 | Auth | POST | `/api/auth/logout` | Authenticated |
| 9 | Auth | POST | `/api/auth/refresh-token` | Public |
| 10 | Auth | POST | `/api/auth/reset-password` | Public |
| 11 | Auth | POST | `/api/auth/change-password` | Authenticated |
| 12 | User | GET | `/api/users/all` | ADMIN |
| 13 | Team | GET | `/api/teams` | Public |
| 14 | Team | GET | `/api/teams/{id}` | Public |
| 15 | Team | POST | `/api/teams` | ADMIN |
| 16 | Team | PUT | `/api/teams/{id}` | ADMIN |
| 17 | Team | DELETE | `/api/teams/{id}` | ADMIN |
| 18 | Team | POST | `/api/teams/{id}/members` | ADMIN |
| 19 | SiteSettings | GET | `/api/site-settings` | Public |
| 20 | SiteSettings | GET | `/api/site-settings/{key}` | Public |
| 21 | SiteSettings | POST | `/api/site-settings` | Header: X-User-Role: admin |
| 22 | SiteSettings | DELETE | `/api/site-settings/{key}` | Header: X-User-Role: admin |
| 23 | Menu | GET | `/api/menus` | Public |
| 24 | Menu | POST | `/api/menus` | ADMIN |
| 25 | Menu | PUT | `/api/menus/{id}` | ADMIN |
| 26 | Menu | DELETE | `/api/menus/{id}` | ADMIN |
| 27 | Menu | PATCH | `/api/menus/{id}/toggle` | ADMIN |
| 28 | Menu | PATCH | `/api/menus/{id}/reorder` | ADMIN |
| 29 | Email | POST | `/api/send-form-receipt` | Authenticated |
| 30 | Email | POST | `/api/send-form-notification` | Authenticated |
| 31 | Email | POST | `/api/send-reply` | ADMIN |
| 32 | Email | POST | `/api/send-email` | Authenticated |
| 33 | Contact | GET | `/api/contact-submissions` | ADMIN |
| 34 | Contact | POST | `/api/contact-submissions` | Public |
| 35 | Contact | PATCH | `/api/contact-submissions/{id}` | ADMIN |
| 36 | Contact | PATCH | `/api/contact-submissions/{id}/notes` | ADMIN |
| 37 | Contact | DELETE | `/api/contact-submissions/{id}` | ADMIN |
| 38 | Chatbot | GET | `/api/chatbot` | Public |
| 39 | Chatbot | POST | `/api/chatbot` | ADMIN |
| 40 | Chatbot | PUT | `/api/chatbot/{id}` | ADMIN |
| 41 | Chatbot | DELETE | `/api/chatbot/{id}` | ADMIN |
| 42 | Chatbot | POST | `/api/chatbot/{id}/train` | ADMIN |
| 43 | Career | GET | `/api/careers` | Public |
| 44 | Career | POST | `/api/careers` | Authenticated |
| 45 | Career | PUT | `/api/careers/{id}` | Authenticated |
| 46 | Career | DELETE | `/api/careers/{id}` | Authenticated |
| 47 | ActivityLog | GET | `/api/activity/logs/{userId}` | ADMIN |
| 48 | ActivityLog | POST | `/api/activity/logs` | ADMIN |

**Total: 48 Endpoints**

---

## Common HTTP Status Codes

| Code | Meaning |
|---|---|
| `200 OK` | Request succeeded |
| `201 Created` | Resource successfully created |
| `400 Bad Request` | Invalid input or validation error |
| `401 Unauthorized` | Missing or invalid JWT token |
| `403 Forbidden` | Valid token but insufficient role/permissions |
| `404 Not Found` | Requested resource does not exist |
| `423 Locked` | Account locked due to multiple failed login attempts |
| `429 Too Many Requests` | Rate limit exceeded (e.g., OTP requests) |
| `500 Internal Server Error` | Unexpected server error |
