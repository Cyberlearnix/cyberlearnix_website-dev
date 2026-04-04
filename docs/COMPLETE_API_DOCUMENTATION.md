# Cyberlearnix — Complete API Documentation
> **Base URL (Gateway):** `http://localhost:8080`  
> All requests must be sent to the gateway. The gateway forwards them to the right microservice.

---

## Table of Contents
1. [Authentication & Authorization](#1-authentication--authorization)
2. [User Service](#2-user-service)
3. [Admin Service](#3-admin-service)
4. [Course Service](#4-course-service)
5. [Enrollment Service](#5-enrollment-service)
6. [Form Service](#6-form-service)
7. [Shop Service](#7-shop-service)
8. [CMS Service](#8-cms-service)
9. [Notification Service](#9-notification-service)
10. [Common Patterns & Error Codes](#10-common-patterns--error-codes)

---

## General Notes

### Authentication
Most protected endpoints require a JWT in the `Authorization` header:
```
Authorization: Bearer <access_token>
```
The token is returned on login. It expires and can be refreshed using the refresh token.

### Role-Based Headers
The gateway injects user identity into downstream requests via custom headers:
| Header | Description |
|---|---|
| `X-User-Id` | The logged-in user's ID |
| `X-User-Role` | The user's role: `admin`, `teacher`, `student`, `dual` |

When calling the gateway directly from the frontend, send the `Authorization` header; the gateway adds `X-User-Id` and `X-User-Role` automatically from the JWT.

---

## 1. Authentication & Authorization
**Service:** `user-service` | **Base Path:** `/api/auth`

### 1.1 Login (Password)
```
POST /api/auth/login
```
**Auth:** None  
**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securepass123"
}
```
**Success Response `200`:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBh...",
  "user": {
    "id": "uuid-string",
    "email": "user@example.com",
    "role": "student"
  }
}
```
**Error Responses:**
- `400` — Missing email or password
- `401` — Invalid credentials

**Frontend:** Store `accessToken` and `refreshToken` in `localStorage`. Attach `accessToken` to every subsequent request as `Authorization: Bearer <token>`.

---

### 1.2 Request OTP for Password Reset
```
POST /api/auth/request-otp
```
**Auth:** None  
**Request Body:**
```json
{ "email": "user@example.com" }
```
**Success Response `200`:**
```json
{
  "success": true,
  "message": "OTP sent to email",
  "sessionId": "session-uuid-string"
}
```
**Error Responses:**
- `400` — Email missing
- `404` — No account with that email
- `429` — Too many OTP requests (wait 15 minutes)

**Frontend:** Save `sessionId`. It is required in the next step (verify-otp).

---

### 1.3 Request OTP for Passwordless Login
```
POST /api/auth/request-login-otp
```
**Auth:** None  
**Request Body:**
```json
{ "email": "user@example.com" }
```
**Success Response `200`:**
```json
{
  "message": "OTP sent",
  "sessionId": "session-uuid-string"
}
```
**Error Responses:**
- `400` — Email missing
- `404` — No account with that email
- `429` — Rate limited

---

### 1.4 Verify OTP (for Password Reset)
```
POST /api/auth/verify-otp
```
**Auth:** None  
**Request Body:**
```json
{
  "email": "user@example.com",
  "otp": "123456",
  "sessionId": "session-uuid-string"
}
```
**Success Response `200`:**
```json
{
  "success": true,
  "resetToken": "reset-token-string"
}
```
**Error Responses:**
- `400` — Missing fields
- `403` — Invalid or expired OTP

**Frontend:** Save `resetToken`. Use it in the reset-password call.

---

### 1.5 Verify OTP for Login (Passwordless)
```
POST /api/auth/verify-otp-login
```
**Auth:** None  
**Request Body:**
```json
{
  "email": "user@example.com",
  "otp": "123456",
  "sessionId": "session-uuid-string"
}
```
**Success Response `200`:** Same as Login response (accessToken + refreshToken + user).

**Error Responses:**
- `400` — Missing fields
- `403` — Invalid or expired OTP

---

### 1.6 Reset Password (using reset token)
```
POST /api/auth/reset-password
```
**Auth:** None  
**Request Body:**
```json
{
  "token": "reset-token-string",
  "newPassword": "newSecure123",
  "confirmPassword": "newSecure123"
}
```
**Success Response `200`:**
```json
{ "success": true, "message": "Password has been reset successfully" }
```
**Error Responses:**
- `400` — Passwords don't match or invalid token

---

### 1.7 Change Password (while logged in)
```
POST /api/auth/change-password
```
**Auth:** Required  
**Request Body:**
```json
{
  "newPassword": "newSecure123",
  "confirmPassword": "newSecure123"
}
```
**Success Response `200`:**
```json
{ "success": true, "message": "Password changed successfully" }
```
**Error Responses:**
- `400` — Passwords don't match
- `401` — Not authenticated

---

### 1.8 Register New User (Admin Only)
```
POST /api/auth/register
```
**Auth:** Required (Admin role only)  
**Request Body:**
```json
{
  "email": "newuser@example.com",
  "password": "securepass123",
  "role": "student"
}
```
> `role` options: `student`, `teacher`, `admin`, `dual`

**Success Response `201`:**
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "user": { "id": "...", "email": "...", "role": "student" }
}
```
**Error Responses:**
- `400` — Missing fields or password too short (< 8 chars)
- `403` — Not an admin
- `409` — Email already exists

---

### 1.9 Refresh Token
```
POST /api/auth/refresh-token
```
**Auth:** None  
**Request Body:**
```json
{ "refreshToken": "dGhpcyBpcyBh..." }
```
**Success Response `200`:**
```json
{ "accessToken": "new-access-token" }
```
**Error Responses:**
- `401` — Invalid or expired refresh token

**Frontend:** Call this automatically when `accessToken` expires (`401` response from any secured endpoint).

---

### 1.10 Logout
```
POST /api/auth/logout
```
**Auth:** Required  
**Headers:** `Authorization: Bearer <accessToken>`  
**Request Body:**
```json
{ "refreshToken": "dGhpcyBpcyBh..." }
```
**Success Response `200`:**
```json
{ "success": true, "message": "Logged out successfully" }
```

---

### 1.11 Switch Role
```
POST /api/auth/switch-role
```
**Auth:** Required (only users with `admin` or `dual` role)  
**Request Body:**
```json
{ "role": "teacher" }
```
**Success Response `200`:** Returns a new token set with the switched role.  
**Error Responses:**
- `403` — Not allowed to switch to the requested role

---

## 2. User Service
**Base Path Prefix:** `/api/users`, `/api/teams`, `/api/site-settings`, `/api/menus`, `/api/careers`, `/api/contact-submissions`, `/api/chatbot`, `/api/activity/logs`

---

### 2.1 Get All Users
```
GET /api/users/all
```
**Auth:** Required (Admin only)  
**Success Response `200`:** Array of user objects:
```json
[
  {
    "id": "uuid",
    "email": "user@example.com",
    "role": "student",
    "fullName": "John Doe",
    "isActive": true
  }
]
```

---

### 2.2 Team Collaboration

#### Get All Teams
```
GET /api/teams
GET /api/teams?courseId=5
```
**Auth:** None  
**Response `200`:** Array of team objects.

#### Get Team by ID
```
GET /api/teams/{id}
```
**Response `200`:** Single team object or `404`.

#### Create Team
```
POST /api/teams
```
**Request Body:**
```json
{
  "name": "Team Alpha",
  "description": "Study group",
  "teamLeaderId": "user-uuid",
  "courseId": 5,
  "maxMembers": 10,
  "isPrivate": true
}
```
> If `isPrivate: true` and no `accessCode` is provided, one is auto-generated.

**Response `200`:** Created team object (includes `accessCode` if private).

#### Update Team
```
PUT /api/teams/{id}
```
**Request Body:** Same fields as create.  
**Response `200`:** Updated team object.

#### Delete Team
```
DELETE /api/teams/{id}
```
**Response `200`:** Empty OK.

#### Add Member to Team
```
POST /api/teams/{id}/members
```
**Request Body:**
```json
{ "userId": 42, "role": 1 }
```
**Response `200`:** Updated team object.

---

### 2.3 Site Settings

#### Get All Settings
```
GET /api/site-settings
GET /api/site-settings?group=PLATFORM
```
**Auth:** None  
**Response `200`:** Array of setting objects:
```json
[
  {
    "id": 1,
    "settingKey": "site_name",
    "settingValue": "Cyberlearnix",
    "settingGroup": "PLATFORM",
    "isActive": true
  }
]
```

#### Get Setting by Key
```
GET /api/site-settings/{key}
```
**Response `200`:** Single setting object or `404`.

#### Create/Update Setting (Admin)
```
POST /api/site-settings
```
**Auth:** Required  
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{
  "settingKey": "site_name",
  "settingValue": "Cyberlearnix Platform",
  "settingGroup": "PLATFORM",
  "isActive": true
}
```
**Response `200/201`:** Setting object.  
**Error:** `403` — Not admin.

#### Delete Setting (Admin)
```
DELETE /api/site-settings/{key}
```
**Headers:** `X-User-Role: admin`  
**Response `200`:** `{ "success": true }` or `404`.

---

### 2.4 Menu Items

#### Get Active Menus
```
GET /api/menus
GET /api/menus?location=header
```
**Auth:** None  
**Response `200`:** Array of active menu items ordered by `displayOrder`.

#### Create Menu Item
```
POST /api/menus
```
**Request Body:**
```json
{
  "label": "Blog",
  "url": "/blog",
  "location": "header",
  "displayOrder": 5,
  "isActive": true,
  "icon": "file-text",
  "parentId": null,
  "openInNewTab": false,
  "cssClass": ""
}
```
**Response `200`:** Created menu item.

#### Update Menu Item
```
PUT /api/menus/{id}
```
**Request Body:** Same as create.  
**Response `200`:** Updated item or `404`.

#### Toggle Active State
```
PATCH /api/menus/{id}/toggle
```
**Response `200`:** Updated item with toggled `isActive`.

#### Reorder Menu Item
```
PATCH /api/menus/{id}/reorder
```
**Request Body:**
```json
{ "order": 3 }
```
**Response `200`:** Updated item.

#### Delete Menu Item
```
DELETE /api/menus/{id}
```
**Response `200`:** Empty OK.

---

### 2.5 Contact Submissions

#### Get All Submissions (Admin)
```
GET /api/contact-submissions
```
**Auth:** Required (Admin)  
**Response `200`:** Array of contact submission objects (newest first, non-deleted).

#### Submit Contact Form (Public)
```
POST /api/contact-submissions
```
**Auth:** None  
**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "9876543210",
  "message": "I want to know more about courses."
}
```
**Response `200`:** Saved submission. Admin notification email is sent asynchronously.

#### Update Submission (Admin)
```
PATCH /api/contact-submissions/{id}
```
**Auth:** Required (Admin)  
**Request Body:**
```json
{
  "status": "read",
  "adminNotes": "Called the user back."
}
```
**Response `200`:** Updated submission.

#### Add/Update Admin Notes
```
PATCH /api/contact-submissions/{id}/notes
```
**Auth:** Required (Admin)  
**Request Body:**
```json
{ "admin_notes": "Follow up scheduled." }
```

#### Soft Delete Submission (Admin)
```
DELETE /api/contact-submissions/{id}
```
**Auth:** Required (Admin)  
**Response `200`:** Empty OK.

---

### 2.6 Chatbot Responses

#### Get All Responses
```
GET /api/chatbot
GET /api/chatbot?category=courses
```
**Auth:** None  
**Response `200`:** Array of chatbot response objects.

#### Create Chatbot Response
```
POST /api/chatbot
```
**Request Body:**
```json
{
  "intent": "pricing",
  "response": "Our courses start from $49.",
  "category": "pricing",
  "confidenceThreshold": 0.75,
  "requiresFollowup": false,
  "isActive": true
}
```
**Response `200`:** Created response object.

#### Update Chatbot Response
```
PUT /api/chatbot/{id}
```
**Request Body:** Same fields as create + optional `followupQuestions`, `trainingPhrases`.

#### Delete Chatbot Response
```
DELETE /api/chatbot/{id}
```
**Response `200`:** Empty OK.

#### Train Response (track usage)
```
POST /api/chatbot/{id}/train
```
**Request Body:** Any JSON map (usually empty `{}`).  
**Response `200`:** Updated response with incremented `usageCount`.

---

### 2.7 Career / Job Openings

#### Get All Jobs
```
GET /api/careers
GET /api/careers?status=open
GET /api/careers?type=full-time
GET /api/careers?status=open&type=remote
```
**Auth:** None  
**Response `200`:** Array of job opening objects.

#### Create Job Opening
```
POST /api/careers
```
**Request Body:**
```json
{
  "title": "Frontend Developer",
  "type": "full-time",
  "department": "Engineering",
  "location": "Hyderabad / Remote",
  "description": "Job description...",
  "requirements": ["3+ years React", "TypeScript"],
  "responsibilities": ["Build UI components"],
  "status": "open",
  "formId": "form-uuid-for-applications"
}
```
**Response `200`:** Created job opening.

#### Update Job Opening
```
PUT /api/careers/{id}
```
**Request Body:** Same fields as create.  
**Response `200`:** Updated job opening or `404`.

#### Delete Job Opening
```
DELETE /api/careers/{id}
```
**Response `200`:** Empty OK.

---

### 2.8 Activity Logs

#### Get User Activity Logs
```
GET /api/activity/logs/{userId}?limit=10
```
**Auth:** None (internal use)  
**Path Param:** `userId` — The user's UUID.  
**Query Param:** `limit` (default: 10) — Max records to return.  
**Response `200`:** Array of activity log objects (newest first).

#### Log an Activity
```
POST /api/activity/logs
```
**Request Body:**
```json
{
  "userId": "user-uuid",
  "action": "COURSE_VIEWED",
  "details": "Viewed Ethical Hacking course"
}
```
**Response `200`:** Saved log object.

---

### 2.9 Email Endpoints (Authenticated)

#### Send Form Receipt to User
```
POST /api/send-form-receipt
```
**Auth:** Required  
**Request Body:**
```json
{
  "recipientEmail": "student@example.com",
  "formTitle": "Ethical Hacking Enrollment",
  "responses": [
    { "question": "Full Name", "answer": "John Doe" },
    { "question": "Phone", "answer": "9876543210" }
  ]
}
```
**Response `200`:** `{ "message": "Receipt sent" }`

#### Send Form Submission Notification to Admin
```
POST /api/send-form-notification
```
**Auth:** Required  
**Request Body:**
```json
{
  "respondentEmail": "student@example.com",
  "formTitle": "Enrollment Form",
  "answers": [
    { "question": "Name", "answer": "Jane" }
  ]
}
```
**Response `200`:** `{ "message": "Notification sent" }`

#### Send Reply Email to User (Admin Only)
```
POST /api/send-reply
```
**Auth:** Required (Admin)  
**Request Body:**
```json
{
  "to": "student@example.com",
  "subject": "Re: Your inquiry",
  "message": "Thank you for reaching out..."
}
```
**Response `200`:** `{ "success": true, "message": "Email sent" }`

#### Send General Inquiry Email
```
POST /api/send-email
```
**Auth:** Required  
**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "9876543210",
  "message": "I have a question about..."
}
```
**Response `200`:** `{ "success": true, "message": "Inquiry sent" }`

---

## 3. Admin Service
**Base Path Prefix:** `/api/admin`

---

### 3.1 Admin Authentication

#### Admin Login
```
POST /api/admin/auth/login
```
**Auth:** None  
**Request Body:**
```json
{
  "email": "admin@cyberlearnix.com",
  "password": "adminpass123"
}
```
**Success Response `200`:** Same as user login (accessToken + refreshToken + user).  
> The backend validates that the role is `admin`. Non-admins get `403 Forbidden`.

**Error Responses:**
- `403` — Not an administrator
- `401` — Invalid credentials

#### Admin Logout
```
POST /api/admin/auth/logout
```
**Auth:** Required  
**Headers:** `Authorization: Bearer <token>`  
**Request Body:**
```json
{ "refreshToken": "..." }
```
**Response `200`:** `{ "message": "Admin logged out successfully" }`

#### Get Admin Profile
```
GET /api/admin/auth/profile
```
**Auth:** Required  
**Response `200`:** Admin user object or `404`.

#### Update Admin Profile
```
PUT /api/admin/auth/profile
```
**Auth:** Required  
**Request Body:**
```json
{ "email": "newemail@cyberlearnix.com" }
```
**Response `200`:** Updated admin object.

---

### 3.2 User Management (Admin)

#### Get All Users
```
GET /api/admin/users
GET /api/admin/users?role=student
```
**Auth:** Required (Admin)  
**Response `200`:** Array of user objects filtered by role (if provided).

#### Get User Details
```
GET /api/admin/users/{id}
```
**Response `200`:** User object or `404`.

#### Update User Status (Activate/Deactivate)
```
PUT /api/admin/users/{id}/status
```
**Request Body:**
```json
{ "isActive": false }
```
**Response `200`:**
```json
{ "message": "User status updated successfully", "isActive": false }
```

#### Delete User
```
DELETE /api/admin/users/{id}
```
**Response `200`:** `{ "message": "User deleted successfully" }` or `404`.

#### Get All Instructors
```
GET /api/admin/users/instructors
```
**Response `200`:** Array of users with role `teacher` or `instructor`.

---

### 3.3 Platform Settings (Admin)

#### Get All Settings
```
GET /api/admin/settings
GET /api/admin/settings?group=PLATFORM
```
**Response `200`:** Array of site setting objects.

#### Update Platform Settings
```
PUT /api/admin/settings/platform
```
**Request Body:**
```json
{
  "site_name": "Cyberlearnix",
  "support_email": "support@cyberlearnix.com"
}
```
**Response `200`:** `{ "message": "PLATFORM settings updated successfully" }`

#### Update Payment Settings
```
PUT /api/admin/settings/payment
```
**Request Body:** Key-value pairs for payment settings.  
**Response `200`:** `{ "message": "PAYMENT settings updated successfully" }`

#### Update Notification Settings
```
PUT /api/admin/settings/notifications
```
**Request Body:** Key-value pairs for notification settings.  
**Response `200`:** `{ "message": "NOTIFICATION settings updated successfully" }`

---

### 3.4 Reports (Admin)

#### User Statistics
```
GET /api/admin/reports/users
```
**Auth:** Required (Admin)  
**Response `200`:**
```json
{
  "totalUsers": 1250,
  "admins": 3,
  "teachers": 45,
  "students": 1100,
  "duals": 50,
  "others": 52
}
```

#### Course Statistics
```
GET /api/admin/reports/courses
```
**Response `200`:**
```json
{
  "totalCourses": 80,
  "approvedCourses": 65,
  "pendingModeration": 10,
  "rejected": 5
}
```

#### Revenue Statistics
```
GET /api/admin/reports/revenue
```
**Response `200`:**
```json
{
  "totalRevenue": 245000.00,
  "totalOrders": 500,
  "totalSuccessfulOrders": 480
}
```

---

### 3.5 Order / Payment Management (Admin)

#### Get All Orders
```
GET /api/admin/orders
GET /api/admin/orders?status=SUCCESS
```
**Auth:** Required (Admin)  
**Response `200`:** Array of enrollment form response objects (orders).

#### Get Order Details
```
GET /api/admin/orders/{id}
```
**Response `200`:** Single order object or `404`.

#### Update Order Status
```
PUT /api/admin/orders/{id}/status
```
**Request Body:**
```json
{ "status": "APPROVED" }
```
> Status values: `SUCCESS`, `APPROVED`, `REJECTED`, `PENDING`, `REFUNDED`

**Response `200`:** `{ "message": "Order status updated successfully", "status": "APPROVED" }`

#### Process Refund
```
POST /api/admin/orders/{id}/refund
```
**Response `200`:** `{ "message": "Order marked as REFUNDED", "status": "REFUNDED" }`

---

### 3.6 Course Moderation (Admin)

#### Get All Courses
```
GET /api/admin/courses
GET /api/admin/courses?status=PENDING
```
**Auth:** Required (Admin)  
**Response `200`:** Array of course objects.

#### Approve Course
```
PUT /api/admin/courses/{id}/approve
```
**Response `200`:** `{ "message": "Course approved successfully", "status": "APPROVED" }`

#### Reject Course
```
PUT /api/admin/courses/{id}/reject
```
**Request Body:**
```json
{ "reason": "Content does not meet our standards." }
```
**Response `200`:** `{ "message": "Course rejected successfully", "status": "REJECTED" }`

#### Get Course Content (for review)
```
GET /api/admin/courses/content/{courseId}
```
**Response `200`:** Array of module content items for the course.

#### Approve Content Item
```
PUT /api/admin/courses/content/{id}/approve
```
**Response `200`:** `{ "message": "Content approved successfully", "status": "APPROVED" }`

#### Reject Content Item
```
PUT /api/admin/courses/content/{id}/reject
```
**Response `200`:** `{ "message": "Content rejected successfully", "status": "REJECTED" }`

---

## 4. Course Service
**Base Path Prefix:** `/api/courses`, `/api/course-management`, `/api/banners`, `/api/certificates`, `/api/content-reviews`, `/api/materials`, `/api/partners`, `/api/promos`, `/api/suggestions`, `/api/updates`

---

### 4.1 Courses

#### Get Courses
```
GET /api/courses
GET /api/courses?id=5
```
**Auth:** Optional  
**Headers (optional):** `X-User-Id`, `X-User-Role`  
**Logic by role:**
- No headers → all courses returned
- `student` → only active/approved courses
- `admin` → all courses
- `teacher` / `dual` → own courses + assigned courses (dual also sees enrolled courses)

**Response `200`:**
```json
{
  "success": true,
  "courses": [
    {
      "id": 1,
      "title": "Ethical Hacking",
      "description": "...",
      "thumbnailUrl": "https://...",
      "basePrice": 4999.00,
      "gstPercent": 18.0,
      "finalPrice": 5898.82,
      "category": "Cybersecurity",
      "difficultyLevel": "Intermediate",
      "duration": "40 hours",
      "active": true,
      "status": "APPROVED"
    }
  ]
}
```

#### Create Course
```
POST /api/courses
```
**Auth:** Required (Admin, Teacher with permission, or Dual)  
**Headers:** `X-User-Id`, `X-User-Role`  
**Request Body:**
```json
{
  "title": "Ethical Hacking Masterclass",
  "description": "Comprehensive guide to ethical hacking.",
  "thumbnailUrl": "https://res.cloudinary.com/...",
  "contentUrl": "https://...",
  "basePrice": 4999.00,
  "gstPercent": 18.0,
  "finalPrice": 5898.82,
  "category": "Cybersecurity",
  "difficultyLevel": "Intermediate",
  "duration": "40 hours",
  "isActive": true
}
```
**Response `201`:**
```json
{
  "success": true,
  "course": { ...course object... }
}
```
**Error Responses:**
- `403` — Students cannot create courses / teacher lacks permission

#### Update Course
```
PUT /api/courses/{id}
```
**Auth:** Required  
**Headers:** `X-User-Id`, `X-User-Role`  
**Request Body:** Any subset of course fields (only provided fields are updated).  
**Response `200`:** `{ "success": true, "course": {...} }` or `403/404`.

#### Delete Course
```
DELETE /api/courses/{id}
```
**Auth:** Required  
**Headers:** `X-User-Id`, `X-User-Role`  
**Response `200`:** `{ "success": true }` or `403/404`.

#### Get Course Curriculum
```
GET /api/courses/{id}/curriculum
```
**Auth:** None  
**Response `200`:**
```json
{
  "success": true,
  "courseTitle": "Ethical Hacking",
  "modules": [
    {
      "id": 1,
      "title": "Introduction",
      "orderIndex": 1,
      "contents": [...]
    }
  ]
}
```

---

### 4.2 Course Management (Modules & Content)
**Base Path:** `/api/course-management`

#### Create Course (via Management)
```
POST /api/course-management/courses
```
**Auth:** Required  
**Headers:** `X-User-Id`, `X-User-Role`  
**Request Body:** Same as `/api/courses` POST.

#### Get Modules for a Course
```
GET /api/course-management/courses/{courseId}/modules
```
**Response `200`:** Array of module objects with their contents.

#### Create Module
```
POST /api/course-management/courses/{courseId}/modules
```
**Request Body:**
```json
{
  "title": "Module 1: Basics",
  "description": "Introduction module",
  "orderIndex": 1
}
```
**Response `201`:** Created module.

#### Update Module
```
PUT /api/course-management/modules/{id}
```
**Request Body:** Module fields to update.

#### Delete Module
```
DELETE /api/course-management/modules/{id}
```

#### Create Content in Module
```
POST /api/course-management/modules/{moduleId}/contents
```
**Request Body:**
```json
{
  "title": "Lecture 1: What is Hacking?",
  "contentType": "LECTURE",
  "orderIndex": 1,
  "isPublished": true
}
```
> `contentType` options: `LECTURE`, `LAB`, `QUIZ`, `ASSIGNMENT`

#### Update Content
```
PUT /api/course-management/contents/{id}
```

#### Delete Content
```
DELETE /api/course-management/contents/{id}
```

#### Add Lecture to Content
```
POST /api/course-management/contents/{contentId}/lecture
```
**Request Body:**
```json
{
  "videoUrl": "https://res.cloudinary.com/...",
  "durationSeconds": 1800,
  "transcript": "...",
  "attachmentUrls": ["https://..."]
}
```

#### Add Quiz to Content
```
POST /api/course-management/contents/{contentId}/quiz
```
**Request Body:**
```json
{
  "passingScore": 70,
  "timeLimitMinutes": 30,
  "maxAttempts": 3,
  "shuffleQuestions": true
}
```

#### Add Question to Quiz
```
POST /api/course-management/quizzes/{quizId}/questions
```
**Request Body:**
```json
{
  "questionText": "What does SQL injection exploit?",
  "questionType": "MULTIPLE_CHOICE",
  "points": 2,
  "explanation": "SQL injection exploits..."
}
```

#### Add Options to Question
```
POST /api/course-management/questions/{questionId}/options
```
**Request Body:**
```json
[
  { "optionText": "Database vulnerabilities", "isCorrect": true },
  { "optionText": "Network vulnerabilities", "isCorrect": false }
]
```

#### Get Teacher Permissions
```
GET /api/course-management/permissions/{teacherId}
```
**Response `200`:**
```json
{
  "userId": "uuid",
  "canCreateCourses": true,
  "canPublishCourses": false,
  "canManageStudents": true
}
```

#### Update Teacher Permissions
```
PUT /api/course-management/permissions/{teacherId}
```
**Request Body:**
```json
{
  "canCreateCourses": true,
  "canPublishCourses": true,
  "canManageStudents": true
}
```

---

### 4.3 Course Teacher Assignments

#### Get Teacher's Assigned Courses
```
GET /api/courses/teachers?teacherId={id}
```
**Response `200`:** Array of `{ courseId, teacherId, course: { id, title } }`.

#### Get Teachers for a Course
```
GET /api/courses/teachers?courseId={id}
```
**Response `200`:** Array of `{ courseId, teacherId, teacher: { id, fullName } }`.

#### Assign Teacher to Course (Admin)
```
POST /api/courses/teachers
```
**Auth:** Required (Admin)  
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{ "courseId": 5, "teacherId": "teacher-uuid" }
```
**Response `200`:** `{ "success": true }` or message if already assigned.

#### Remove Teacher from Course (Admin)
```
DELETE /api/courses/teachers
```
**Auth:** Required (Admin)  
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{ "courseId": 5, "teacherId": "teacher-uuid" }
```
**Response `200`:** `{ "success": true }`

---

### 4.4 Progress Tracking

#### Update Content Progress
```
POST /api/courses/progress/update
```
**Auth:** Required  
**Headers:** `X-User-Id: <studentId>`  
**Request Body:**
```json
{
  "contentId": 42,
  "status": "COMPLETED",
  "videoTime": 1800,
  "score": 85.0
}
```
> `status` options: `STARTED`, `COMPLETED`

**Response `200`:**
```json
{
  "success": true,
  "progress": {
    "studentId": "uuid",
    "contentId": 42,
    "status": "COMPLETED",
    "isCompleted": true,
    "score": 85.0,
    "videoTimeSeconds": 1800
  }
}
```
> Overall course progress percentage is recalculated automatically.

#### Get Course Progress
```
GET /api/courses/progress/{courseId}
```
**Auth:** Required  
**Headers:** `X-User-Id: <studentId>`  
**Response `200`:**
```json
{
  "success": true,
  "overallProgress": 65,
  "items": [
    {
      "contentId": 42,
      "status": "COMPLETED",
      "isCompleted": true,
      "score": 85.0
    }
  ]
}
```

---

### 4.5 Certificates

#### Get All Certificates
```
GET /api/certificates
```
**Response `200`:** Array of certificate objects.

#### Issue Certificate
```
POST /api/certificates
```
**Request Body:**
```json
{
  "studentId": "student-uuid",
  "courseId": 5,
  "issuedAt": "2026-04-01T10:00:00",
  "certificateUrl": "https://..."
}
```
**Response `200`:** Issued certificate object.

#### Delete Certificate
```
DELETE /api/certificates/{id}
```
**Response `200`:** Empty OK or `404`.

#### Get Certificate Templates
```
GET /api/certificates/templates
```
**Response `200`:** Array of template objects.

#### Update Certificate Template
```
PUT /api/certificates/templates/{id}
```
**Request Body:**
```json
{ "backgroundUrl": "https://res.cloudinary.com/..." }
```
**Response `200`:** Updated template.

---

### 4.6 Banners

#### Get All Banners
```
GET /api/banners
```
**Auth:** None  
**Response `200`:** Array of banner objects:
```json
[
  {
    "id": 1,
    "title": "Learn Cybersecurity",
    "subtitle": "From experts.",
    "imgUrl": "https://...",
    "buttons": [...],
    "displayOrder": 1
  }
]
```

#### Create Banner (Admin)
```
POST /api/banners
```
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{
  "title": "New Course Available",
  "subtitle": "Enroll now!",
  "imgUrl": "https://...",
  "buttons": [{ "label": "Enroll Now", "url": "/courses/5" }],
  "displayOrder": 1
}
```
**Response `201`:** Created banner.  
**Error:** `403` — Not admin.

#### Update Banner (Admin)
```
PUT /api/banners/{id}
```
**Headers:** `X-User-Role: admin`  
**Request Body:** Any banner fields to update.

#### Delete Banner (Admin)
```
DELETE /api/banners/{id}
```
**Headers:** `X-User-Role: admin`  
**Response `200`:** `{ "success": true }` or `404`.

---

### 4.7 Content Reviews

#### Get All Reviews
```
GET /api/content-reviews
GET /api/content-reviews?status=pending
GET /api/content-reviews?teacherId=5
```
**Response `200`:** Array of content review objects.

#### Get Pending Reviews
```
GET /api/content-reviews/pending
```
**Response `200`:** Array of pending review objects.

#### Submit Content for Review
```
POST /api/content-reviews
```
**Request Body:**
```json
{
  "contentId": 42,
  "teacherId": 10,
  "content": "Lecture transcript or description..."
}
```
**Response `200`:** Review object with `reviewStatus: "pending"`.

#### Review Content (Admin/Reviewer)
```
PATCH /api/content-reviews/{id}/review
```
**Request Body:**
```json
{
  "reviewerId": 1,
  "notes": "Looks great!",
  "approved": true,
  "requiresRevision": false
}
```
> If `requiresRevision: true`, provide `revisionNotes` and status becomes `revision_required`.

**Response `200`:** Updated review object.

#### Submit Revision
```
PATCH /api/content-reviews/{id}/revise
```
**Request Body:**
```json
{ "content": "Updated lecture content..." }
```
**Response `200`:** Review reset to `pending` status.

#### Delete Review
```
DELETE /api/content-reviews/{id}
```
**Response `200`:** Empty OK.

---

### 4.8 Material Uploads (Cloudinary)

#### Upload Course Thumbnail
```
POST /api/materials/upload/thumbnail
Content-Type: multipart/form-data
```
**Auth:** Required  
**Headers:** `X-User-Id`  
**Form Data:** `file` — image file (max 5MB, images only)  
**Response `200`:**
```json
{ "success": true, "url": "https://res.cloudinary.com/..." }
```

#### Upload Lecture Video
```
POST /api/materials/upload/video
Content-Type: multipart/form-data
```
**Auth:** Required (Teacher or Admin only)  
**Headers:** `X-User-Id`, `X-User-Role`  
**Form Data:** `file` — video file (max 500MB)  
**Response `200`:**
```json
{ "success": true, "url": "https://res.cloudinary.com/..." }
```

#### Upload Document/Attachment
```
POST /api/materials/upload/document
Content-Type: multipart/form-data
```
**Auth:** Required (Teacher or Admin only)  
**Form Data:** `file` — PDF, ZIP, etc. (max 50MB)  
**Response `200`:**
```json
{ "success": true, "url": "https://res.cloudinary.com/..." }
```

#### Upload Banner Image
```
POST /api/materials/upload/banner
Content-Type: multipart/form-data
```
**Auth:** Required (Admin only)  
**Form Data:** `file` — image file  
**Response `200`:**
```json
{ "success": true, "url": "https://res.cloudinary.com/..." }
```

> **Frontend Upload Flow:**
> 1. Upload file to `/api/materials/upload/*` → get `url`
> 2. Use that `url` when creating/updating course, lecture, or banner data

---

### 4.9 Content Partners

#### Get All Partners
```
GET /api/partners
```
**Auth:** None  
**Response `200`:** `{ "success": true, "partners": [...] }`

#### Create Partner
```
POST /api/partners
```
**Request Body:**
```json
{
  "name": "CISCO",
  "url": "https://cisco.com",
  "logoUrl": "https://..."
}
```
**Response `201`:** `{ "success": true, "partner": {...} }`

#### Update Partner
```
PUT /api/partners/{id}
```
**Request Body:**
```json
{ "name": "CISCO Systems", "url": "https://cisco.com", "logo_url": "https://..." }
```
**Response `200`:** `{ "success": true, "partner": {...} }`

#### Delete Partner
```
DELETE /api/partners/{id}
```
**Response `200`:** `{ "success": true }` or `404`.

---

### 4.10 Promo Banners

#### Get All Promos
```
GET /api/promos
```
**Auth:** None  
**Response `200`:** Array of promo banner objects.

#### Create Promo (Admin)
```
POST /api/promos
```
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{
  "title": "50% Off All Courses",
  "description": "Limited time offer!",
  "imgUrl": "https://...",
  "link": "/courses",
  "status": "active"
}
```
**Response `201`:** Created promo.

#### Update Promo (Admin)
```
PUT /api/promos/{id}
```
**Headers:** `X-User-Role: admin`  
**Request Body:** Any promo fields.

#### Delete Promo (Admin)
```
DELETE /api/promos/{id}
```
**Headers:** `X-User-Role: admin`  
**Response `200`:** `{ "success": true }`

---

### 4.11 Course Suggestions (Admin → Teacher)

#### Get Suggestions
```
GET /api/suggestions
GET /api/suggestions?courseId=5
```
**Auth:** Required  
**Headers:** `X-User-Id`, `X-User-Role`  
- Admin sees all; Teacher sees only their courses' suggestions.

**Response `200`:** `{ "success": true, "suggestions": [...] }`

#### Create Suggestion (Admin Only)
```
POST /api/suggestions
```
**Headers:** `X-User-Id`, `X-User-Role: admin`  
**Request Body:**
```json
{
  "courseId": 5,
  "suggestionText": "Add more practical exercises to Module 3."
}
```
**Response `201`:** Created suggestion with `status: "pending"`.

#### Update Suggestion Status (Teacher response)
```
PUT /api/suggestions/{id}
```
**Headers:** `X-User-Id`, `X-User-Role`  
**Request Body:**
```json
{ "status": "acknowledged" }
```
> Status values: `pending`, `acknowledged`, `implemented`

---

### 4.12 Content Updates

#### Get All Updates
```
GET /api/updates
```
**Response `200`:** Array of content update objects.

#### Create Update
```
POST /api/updates
```
**Request Body:**
```json
{
  "courseId": 5,
  "updateText": "Added new lab exercises to Module 2.",
  "updateType": "CONTENT_ADD"
}
```

#### Update an Update Entry
```
PUT /api/updates/{id}
```
**Request Body:** Fields to update.

---

## 5. Enrollment Service
**Base Path Prefix:** `/api/enrollments`, `/api/payu-payment`

---

### 5.1 Enrollments

#### Get Enrollments
```
GET /api/enrollments
GET /api/enrollments?studentId=uuid&courseId=5
```
**Auth:** Required  
**Headers:** `X-User-Id`, `X-User-Role`  
**Logic by role:**
- `student` → sees only their own enrollments
- `admin` → sees all enrollments
- `teacher/dual` → sees enrollments for their assigned courses (must provide `courseId`)

**Response `200`:**
```json
{
  "success": true,
  "enrollments": [
    {
      "id": 1,
      "studentId": "uuid",
      "course": { "id": 5, "title": "Ethical Hacking" },
      "enrolledAt": "2026-03-01T10:00:00",
      "progress": 45,
      "completedAt": null
    }
  ]
}
```

#### Get Enrollment Config (by Form)
```
GET /api/enrollments/config?formId=form-uuid
GET /api/enrollments/config?formId=form-uuid&token=access-token
```
**Response `200`:** Enrollment form configuration object.

#### Create Enrollment
```
POST /api/enrollments
```
**Auth:** Required  
**Request Body:**
```json
{
  "studentId": "student-uuid",
  "courseId": 5
}
```
**Response `201`:** `{ "success": true, "enrollment": {...} }`  
**Error:** `400` — Course not found.

#### Update Enrollment Progress
```
PUT /api/enrollments/{id}
```
**Request Body:**
```json
{
  "progress": 75,
  "completedAt": "2026-04-01T00:00:00"
}
```
**Response `200`:** `{ "success": true, "enrollment": {...} }`

#### Verify Payment (Admin Only)
```
POST /api/enrollments/verify-payment
```
**Auth:** Required (Admin)  
**Headers:** `X-User-Id`, `X-User-Role: admin`  
**Request Body:**
```json
{
  "enrollmentId": 10,
  "action": "APPROVE",
  "rejectionReason": ""
}
```
> `action` options: `APPROVE`, `REJECT`

#### Bulk Assign Courses to Student (Admin Only)
```
POST /api/enrollments/bulk-assign
```
**Headers:** `X-User-Role: admin`  
**Request Body:**
```json
{
  "userId": "student-uuid",
  "courseIds": [1, 2, 5]
}
```
**Response `200`:** `{ "success": true, "enrollments": [...] }`

---

### 5.2 Payment (PayU Gateway)

#### Initiate Payment
```
POST /api/payu-payment
```
**Auth:** None  
**Request Body:**
```json
{
  "amount": "5898.82",
  "courseName": "Ethical Hacking Masterclass",
  "studentName": "John Doe",
  "studentEmail": "john@example.com",
  "studentPhone": "9876543210",
  "formId": "form-uuid",
  "enrollmentFormResponseId": 42
}
```
**Success Response `200`:**
```json
{
  "success": true,
  "paymentData": {
    "key": "merchant-key",
    "txnid": "TXN1712000000000",
    "amount": "5898.82",
    "productinfo": "Ethical Hacking Masterclass",
    "firstname": "John Doe",
    "email": "john@example.com",
    "phone": "9876543210",
    "surl": "http://localhost:3000/enroll-form.html?status=success&...",
    "furl": "http://localhost:3000/enroll-form.html?status=failure&...",
    "hash": "sha512-hash-string",
    "action": "https://secure.payu.in/_payment"
  }
}
```

**Frontend Payment Flow:**
1. Call `/api/payu-payment` with order details → get `paymentData`
2. Create an HTML form with `paymentData` fields and `action` as form action
3. Submit form to PayU
4. PayU redirects to `surl` (success) or `furl` (failure)
5. On success redirect: call `/api/enrollments/responses/{id}/finalize` with transaction details

---

### 5.3 Enrollment Forms

#### Get All Form Configs
```
GET /api/enrollments/forms
GET /api/enrollments/forms?view=trash
```
**Response `200`:** Array of enrollment form config objects.

#### Get Form Config by ID
```
GET /api/enrollments/forms/{id}
GET /api/enrollments/forms/{id}?token=access-token
```
> Use `token` for public access without admin auth.

#### Create Form Config
```
POST /api/enrollments/forms
```
**Request Body:**
```json
{
  "title": "Ethical Hacking Enrollment Form",
  "courseId": 5,
  "description": "Fill this form to enroll.",
  "fields": [...],
  "active": true,
  "startTime": "2026-01-01T00:00:00",
  "endTime": "2026-12-31T00:00:00",
  "quiz": false,
  "limitOneResponse": true
}
```

#### Update Form Config
```
PUT /api/enrollments/forms/{id}
```

#### Delete Form Config
```
DELETE /api/enrollments/forms/{id}
DELETE /api/enrollments/forms/{id}?permanent=true
```
> Default: soft delete. Add `?permanent=true` for hard delete.

#### Restore Deleted Form
```
POST /api/enrollments/forms/{id}/restore
```

#### Toggle Form Active State
```
PATCH /api/enrollments/forms/{id}/active
```
**Request Body:** `{ "active": true }`

#### Duplicate Form
```
POST /api/enrollments/forms/{id}/duplicate
```
**Response `200`:** Copied form config with new ID.

---

### 5.4 Enrollment Submissions (Legacy)

#### Get All Submissions
```
GET /api/enrollments/submissions
GET /api/enrollments/submissions?status=pending
```
**Response `200`:** Array of non-deleted submission objects.

#### Create Submission
```
POST /api/enrollments/submissions
```
**Request Body:** Enrollment submission object.  
**Response `200`:** Saved submission with `status: "pending"`.

#### Update Submission Status (Admin)
```
PATCH /api/enrollments/submissions/{id}/status
```
**Headers:** `X-User-Id`  
**Request Body:**
```json
{
  "status": "approved",
  "rejectionReason": ""
}
```
**Response `200`:** `{ "success": true, "status": "approved" }`

#### Soft Delete Submission
```
DELETE /api/enrollments/submissions/{id}
```
**Response `200`:** `{ "success": true }`

---

### 5.5 Enrollment Form Responses

#### Get All Responses
```
GET /api/enrollments/responses
GET /api/enrollments/responses?view=trash
```
**Response `200`:** Response map with array of response objects.

#### Check if Already Responded
```
GET /api/enrollments/responses/check?formId={id}&email={email}
```
**Response `200`:** `{ "alreadyResponded": true }`

#### Submit Response
```
POST /api/enrollments/responses
```
**Request Body:**
```json
{
  "formId": "form-uuid",
  "studentEmail": "john@example.com",
  "studentData": "{\"name\": \"John\", \"phone\": \"9876543210\"}",
  "paymentStatus": "PENDING"
}
```
**Response `200`:** Saved response object.

#### Finalize Response (after payment success)
```
POST /api/enrollments/responses/{id}/finalize
```
**Request Body:**
```json
{
  "transactionId": "TXN1712000000000",
  "paymentTxnid": "TXN1712000000000",
  "amount": 5898.82
}
```
**Response `200`:** Updated response with `paymentStatus: "PAID"`. Admin is notified.

#### Update Response
```
PUT /api/enrollments/responses/{id}
```
**Request Body:** Fields to update (`studentData`, `paymentStatus`, `transactionId`).

#### Update Response by Transaction ID
```
PUT /api/enrollments/responses/by-txn/{txnid}
```
**Request Body:** Same as above.

#### Delete Response
```
DELETE /api/enrollments/responses/{id}
DELETE /api/enrollments/responses/{id}?permanent=true
DELETE /api/enrollments/responses/{id}?legacy=true
```

#### Restore Response
```
POST /api/enrollments/responses/{id}/restore
POST /api/enrollments/responses/{id}/restore?legacy=true
```

#### Bulk Submit Responses
```
POST /api/enrollments/responses/bulk
```
**Request Body:**
```json
[
  { "formId": "form-uuid", "studentData": { "name": "John" } }
]
```

---

### 5.6 Enrollment Workflows

#### Get All Workflows
```
GET /api/enrollments/workflows
```
**Response `200`:** Array of workflow objects.

#### Get Workflow by ID
```
GET /api/enrollments/workflows/{id}
```

#### Create Workflow
```
POST /api/enrollments/workflows
```
**Request Body:**
```json
{
  "name": "Express Enrollment",
  "description": "Fast-track enrollment",
  "autoApprove": true,
  "paymentRequired": true,
  "depositAmount": 1000.0,
  "steps": {
    "1": "Pay deposit",
    "2": "Auto confirm"
  },
  "isActive": true
}
```

#### Update Workflow
```
PUT /api/enrollments/workflows/{id}
```

#### Delete Workflow
```
DELETE /api/enrollments/workflows/{id}
```

#### Set Default Workflow
```
PATCH /api/enrollments/workflows/{id}/default
```
**Response `200`:** Updated workflow with `isDefault: true`. All others set to `false`.

---

## 6. Form Service
**Base Path:** `/api/forms`

General-purpose form builder (not enrollment-specific).

---

### 6.1 Forms (Admin)

#### Get All Forms
```
GET /api/forms
GET /api/forms?view=active
GET /api/forms?view=trash
```
**Auth:** Required (Admin)  
**Response `200`:** Array of `FormResponseDTO` objects.

#### Get Form by ID
```
GET /api/forms/{id}
GET /api/forms/{id}?token=access-token
```
> Public access via `token`, admin access without token.

**Response `200`:** `FormResponseDTO` object.

#### Create Form
```
POST /api/forms
```
**Auth:** Required (Admin)  
**Request Body (`FormRequestDTO`):**
```json
{
  "title": "Career Application Form",
  "description": "Apply for a position at Cyberlearnix",
  "fields": [
    {
      "label": "Full Name",
      "type": "text",
      "required": true
    },
    {
      "label": "Resume URL",
      "type": "url",
      "required": true
    }
  ],
  "active": true,
  "limitOneResponse": true
}
```

#### Update Form
```
PUT /api/forms/{id}
```
**Auth:** Required (Admin)  
**Request Body:** Same as create.

#### Toggle Active
```
PATCH /api/forms/{id}/active
```
**Request Body:** `{ "active": false }`

#### Duplicate Form
```
POST /api/forms/{id}/duplicate
```
**Response `200`:** New duplicated `FormResponseDTO`.

#### Delete Form
```
DELETE /api/forms/{id}
DELETE /api/forms/{id}?permanent=true
```

#### Restore Form
```
POST /api/forms/{id}/restore
```

#### Get All Form Responses (Admin)
```
GET /api/forms/responses
```
**Auth:** Required (Admin)  
**Response `200`:** Array of all submissions across all forms.

---

### 6.2 Form Responses / Submissions

#### Submit Response (Public)
```
POST /api/forms/{formId}/responses
```
**Auth:** None  
**Request Body (`SubmissionRequestDTO`):**
```json
{
  "email": "applicant@example.com",
  "answers": [
    { "fieldId": "field-uuid", "value": "John Doe" },
    { "fieldId": "field-uuid-2", "value": "https://resume.pdf" }
  ]
}
```
**Response `200`:** `SubmissionResponseDTO` with submission ID.

#### Check if Already Submitted
```
GET /api/forms/{formId}/responses/check?email={email}
```
**Response `200`:** `{ "alreadyResponded": true }`

#### Get Responses for a Form (Admin)
```
GET /api/forms/{formId}/responses
```
**Auth:** Required (Admin)  
**Response `200`:** Array of `SubmissionResponseDTO`.

#### Get Analytics for a Form (Admin)
```
GET /api/forms/{formId}/responses/analytics
```
**Auth:** Required (Admin)  
**Response `200`:** `FormAnalyticsDTO` object with field-level breakdown.

#### Export Responses as CSV (Admin)
```
GET /api/forms/{formId}/responses/export
```
**Auth:** Required (Admin)  
**Response `200`:** CSV file download (`Content-Disposition: attachment; filename="responses-{formId}.csv"`).

---

## 7. Shop Service
**Base Path:** `/api/shop`

---

### 7.1 Get Shop Settings
```
GET /api/shop
```
**Auth:** None  
**Response `200`:**
```json
{
  "id": 1,
  "enabled": true,
  "shopUrl": "https://shop.cyberlearnix.com",
  "announcementText": "New courses added!",
  "name": "Cyberlearnix Shop",
  "description": "Official store",
  "currency": "INR",
  "theme": "dark"
}
```
> Returns `{ "enabled": false }` if not configured yet.

### 7.2 Update Shop Settings (Admin)
```
PUT /api/shop
```
**Auth:** Required (Admin)  
**Request Body (`ShopSettingsDTO`):**
```json
{
  "enabled": true,
  "shopUrl": "https://shop.cyberlearnix.com",
  "announcementText": "Summer sale is live!",
  "name": "Cyberlearnix Shop",
  "description": "Your one-stop learning store",
  "currency": "INR",
  "theme": "dark"
}
```
**Response `200`:** `{ "success": true, "data": { ...updated settings... } }`

---

## 8. CMS Service
**Base Path:** `/api/cms`

---

### 8.1 Pages

#### Get All Pages
```
GET /api/cms/pages
```
**Auth:** None  
**Response `200`:** Array of page objects.

#### Get Page by Slug
```
GET /api/cms/pages/{slug}
```
**Auth:** None  
**Response `200`:** Page object or `404`.

#### Create Page (Admin)
```
POST /api/cms/pages
```
**Auth:** Required (Admin)  
**Request Body (`PageCreateDTO`):**
```json
{
  "title": "About Us",
  "slug": "about",
  "metaTitle": "About Cyberlearnix",
  "metaDescription": "Learn about our mission.",
  "isPublished": true
}
```
**Response `201`:** Created page.

#### Update Page (Admin)
```
PUT /api/cms/pages/{id}
```
**Request Body:** Same as create.  
**Response `200`:** Updated page or `404`.

#### Delete Page (Admin)
```
DELETE /api/cms/pages/{id}
```
**Response `204`:** No content.

---

### 8.2 Page Sections

#### Add Section to Page
```
POST /api/cms/pages/{pageId}/sections
```
**Auth:** Required (Admin)  
**Request Body:**
```json
{
  "title": "Our Mission",
  "sectionType": "TEXT",
  "orderIndex": 1,
  "content": "We empower cybersecurity professionals..."
}
```
**Response `201`:** Created section or `404` if page not found.

---

### 8.3 Page Components

#### Add Component to Section
```
POST /api/cms/sections/{sectionId}/components
```
**Auth:** Required (Admin)  
**Request Body:**
```json
{
  "componentType": "IMAGE",
  "content": "https://...",
  "orderIndex": 1
}
```
**Response `201`:** Created component or `404` if section not found.

---

### 8.4 Media

#### Get All Media Files
```
GET /api/cms/media
```
**Auth:** None  
**Response `200`:** Array of media file objects.

#### Upload Media File
```
POST /api/cms/media/upload
Content-Type: multipart/form-data
```
**Auth:** Required  
**Form Data:**
- `file` — The file to upload
- `type` (optional, default: `image`) — `image`, `video`, `document`
- `uploadedBy` (optional) — Uploader user ID

**Response `200`:** Saved media file object with URL.

---

## 9. Notification Service
**Base Path:** `/api/notifications`

All actions are sent as a single endpoint with an `action` query parameter.

```
POST /api/notifications?action={actionName}
```

**Auth:** Required (internal service calls)

### Actions and Their Request Bodies

| Action | Description | Required Fields in `data` |
|---|---|---|
| `broadcast` | Send bulk email to a list | `emails[]`, `subject`, `message` (top-level fields) |
| `contact` | Forward contact form to admin | `name`, `email`, `phone`, `message` |
| `send-confirmation` | Enrollment confirmation to student | `email`, `name`, `courseName` |
| `send-form-confirmation` | Form submission confirmation | `email`, `formTitle` |
| `send-account-credentials` | Send login credentials to new user | `email`, `password`, `name` |
| `send-admin-payment-alert` | Notify admin of new payment | `studentName`, `courseName`, `amount` |
| `send-credentials` | Resend credentials | `email`, `password` |
| `send-verified` | Notify student enrollment approved | `email`, `courseName` |
| `send-rejected` | Notify student enrollment rejected | `email`, `courseName`, `reason` |
| `invite` | Send form invitation link | `email`, `formTitle`, `formUrl` |
| `share-excel` | Email CSV/Excel of responses | `email`, `formTitle`, `attachmentUrl` |

**Request Body:**
```json
{
  "emails": ["a@b.com"],
  "subject": "Welcome to Cyberlearnix",
  "message": "Hello...",
  "data": {
    "key": "value"
  }
}
```

**Success Response `200`:** `{ "success": true }`  
**Error Responses:**
- `400` — Invalid action
- `500` — Email delivery failed

---

## 10. Common Patterns & Error Codes

### Standard Error Response Format
```json
{ "error": "Human-readable error message" }
```

### HTTP Status Codes Used
| Code | Meaning |
|---|---|
| `200` | Success |
| `201` | Created |
| `204` | No Content (delete) |
| `400` | Bad Request — missing/invalid fields |
| `401` | Unauthorized — missing or expired token |
| `403` | Forbidden — insufficient role/permission |
| `404` | Not Found |
| `409` | Conflict — e.g., email already exists |
| `429` | Too Many Requests — rate limited |
| `500` | Internal Server Error |

### Roles
| Role | Capabilities |
|---|---|
| `admin` | Full access to all APIs |
| `teacher` | Manage own courses, view student progress for assigned courses |
| `student` | View active courses, track own progress, submit enrollment forms |
| `dual` | Combined teacher + student access |

### Token Refresh Strategy (Frontend)
```javascript
// On any 401 response from an API call:
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  const res = await fetch('/api/auth/refresh-token', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    headers: { 'Content-Type': 'application/json' }
  });
  if (res.ok) {
    const data = await res.json();
    localStorage.setItem('accessToken', data.accessToken);
    return data.accessToken;
  } else {
    // Refresh token expired — redirect to login
    localStorage.clear();
    window.location.href = '/login';
  }
}
```

### Standard Fetch Helper (Frontend)
```javascript
async function apiCall(path, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    ...options.headers
  };

  let res = await fetch(`http://localhost:8080${path}`, { ...options, headers });

  if (res.status === 401) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`;
      res = await fetch(`http://localhost:8080${path}`, { ...options, headers });
    }
  }

  return res.json();
}
```

### File Upload Helper (Frontend)
```javascript
async function uploadFile(endpoint, file) {
  const token = localStorage.getItem('accessToken');
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`http://localhost:8080${endpoint}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },
    body: formData
    // Note: Do NOT set Content-Type header for multipart — browser sets it automatically
  });
  return res.json(); // Returns { success: true, url: "https://..." }
}
```

---

*Document generated from source code — April 2026*
