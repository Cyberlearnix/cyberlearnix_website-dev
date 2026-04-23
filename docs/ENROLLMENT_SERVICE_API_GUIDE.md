# Enrollment Service — Frontend API Guide

**Base URL:** `http://localhost:8083`  
**Service:** enrollment-service  
**Total APIs:** 36  

> **Note for Frontend:** All requests go through the API Gateway (`http://localhost:8080`). The gateway forwards headers like `X-User-Id` and `X-User-Role` automatically from the JWT token. For direct service calls during development, pass these headers manually.

---

## Table of Contents

1. [Enrollments](#1-enrollments)
2. [Enrollment Forms](#2-enrollment-forms)
3. [Form Responses](#3-form-responses)
4. [Submissions](#4-submissions)
5. [Workflows](#5-workflows)
6. [Payment (PayU)](#6-payment-payu)
7. [Admin Stats](#7-admin-stats)
8. [Complete Flow Diagram](#8-complete-enrollment-flow)
9. [Error Reference](#9-error-reference)

---

## 1. Enrollments

**Base path:** `/api/enrollments`

Manages which students are enrolled in which courses, and tracks their progress.

---

### 1.1 GET — Fetch Enrollment Config
`GET /api/enrollments/config`

Fetches a form configuration linked to a formId.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `formId` | Yes | The form ID to fetch config for |
| `token` | No | Access token for private forms |

```bash
# Without token (public form)
curl -X GET "http://localhost:8083/api/enrollments/config?formId=form_abc123"

# With token (private form)
curl -X GET "http://localhost:8083/api/enrollments/config?formId=form_abc123&token=secret_token"
```

**Success Response (200):**
```json
{
  "id": "form_abc123",
  "title": "Cybersecurity Fundamentals Application",
  "courseId": 10,
  "active": true,
  "description": "Fill to apply for this course",
  "fields": [
    { "name": "phone", "label": "Phone Number", "type": "text", "required": true },
    { "name": "education", "label": "Highest Education", "type": "select", "options": ["10th","12th","Graduate"], "required": true }
  ],
  "startTime": "2026-04-01T00:00:00",
  "endTime": "2026-05-01T00:00:00",
  "quiz": false,
  "limitOneResponse": true
}
```

**Error Response (404):** Form not found.

---

### 1.2 GET — Fetch Enrollments
`GET /api/enrollments`

Returns enrollments based on the caller's role. Role is detected from `X-User-Role` header.

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-User-Id` | Yes | The logged-in user's ID |
| `X-User-Role` | Yes | `student`, `admin`, `teacher`, or `dual` |

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `studentId` | No | Override student ID (admin use) |
| `courseId` | No | Filter by course ID |

```bash
# Student: fetch their own enrollments
curl -X GET "http://localhost:8083/api/enrollments" \
  -H "X-User-Id: student123" \
  -H "X-User-Role: student"
```
**Response (200):**
```json
{
  "success": true,
  "enrollments": [
    {
      "id": 1,
      "studentId": "student123",
      "courseId": 10,
      "progress": 45,
      "enrolledAt": "2026-04-10T10:00:00",
      "completedAt": null
    },
    {
      "id": 2,
      "studentId": "student123",
      "courseId": 11,
      "progress": 100,
      "enrolledAt": "2026-03-01T08:00:00",
      "completedAt": "2026-04-01T18:30:00"
    }
  ]
}
```

```bash
# Admin: fetch ALL enrollments in the system
curl -X GET "http://localhost:8083/api/enrollments" \
  -H "X-User-Id: admin1" \
  -H "X-User-Role: admin"
```
**Response (200):**
```json
{
  "success": true,
  "enrollments": [ ...all enrollment objects... ]
}
```

```bash
# Teacher: fetch enrollments for their course
curl -X GET "http://localhost:8083/api/enrollments?courseId=10" \
  -H "X-User-Id: teacher1" \
  -H "X-User-Role: teacher"
```

```bash
# Check if a specific student is enrolled in a specific course
curl -X GET "http://localhost:8083/api/enrollments?studentId=student123&courseId=10" \
  -H "X-User-Id: student123" \
  -H "X-User-Role: student"
```
**Response (200) — single object (not array):**
```json
{
  "id": 1,
  "studentId": "student123",
  "courseId": 10,
  "progress": 45,
  "enrolledAt": "2026-04-10T10:00:00",
  "completedAt": null
}
```
**Response (404):** Student not enrolled in that course.

**Response (403):** Insufficient permissions (e.g., teacher not assigned to that course).

---

### 1.3 POST — Create Enrollment
`POST /api/enrollments`

Directly creates an enrollment record. Used after admin approval or for free courses.

**Request Body:**
```json
{
  "studentId": "student123",
  "courseId": 10
}
```

```bash
curl -X POST "http://localhost:8083/api/enrollments" \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": "student123",
    "courseId": 10
  }'
```

**Success Response (201):**
```json
{
  "success": true,
  "enrollment": {
    "id": 5,
    "studentId": "student123",
    "courseId": 10,
    "progress": 0,
    "enrolledAt": "2026-04-17T09:30:00",
    "completedAt": null
  }
}
```

---

### 1.4 PUT — Update Progress by Enrollment ID
`PUT /api/enrollments/{id}`

Updates the progress percentage of a specific enrollment. When progress reaches 100, it can auto-set `completedAt`.

**Path Parameter:** `id` — the enrollment ID

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `progress` | integer (0–100) | No | New progress percentage |
| `completedAt` | string (ISO datetime) | No | Manually set completion timestamp |

```bash
# Update progress only
curl -X PUT "http://localhost:8083/api/enrollments/5" \
  -H "Content-Type: application/json" \
  -d '{
    "progress": 75
  }'
```
**Success Response (200):**
```json
{
  "success": true,
  "enrollment": {
    "id": 5,
    "studentId": "student123",
    "courseId": 10,
    "progress": 75,
    "enrolledAt": "2026-04-10T10:00:00",
    "completedAt": null
  }
}
```

```bash
# Mark as completed with a specific timestamp
curl -X PUT "http://localhost:8083/api/enrollments/5" \
  -H "Content-Type: application/json" \
  -d '{
    "progress": 100,
    "completedAt": "2026-04-17T12:00:00"
  }'
```

**Error Response (404):** Enrollment not found.

---

### 1.5 PATCH — Update Progress by Student + Course
`PATCH /api/enrollments/progress`

Same as PUT above but uses `studentId` + `courseId` instead of enrollment ID. More convenient for frontend when you don't store the enrollment ID.

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `studentId` | string | Yes | The student's ID |
| `courseId` | number | Yes | The course ID |
| `progress` | integer (0–100) | No | New progress percentage |
| `completedAt` | string (ISO datetime) | No | Manual completion timestamp |

```bash
curl -X PATCH "http://localhost:8083/api/enrollments/progress" \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": "student123",
    "courseId": 10,
    "progress": 80
  }'
```

**Success Response (200):**
```json
{
  "success": true,
  "enrollment": {
    "id": 5,
    "studentId": "student123",
    "courseId": 10,
    "progress": 80,
    "enrolledAt": "2026-04-10T10:00:00",
    "completedAt": null
  }
}
```

**Error Response (404):** No enrollment found for that student+course combination.

---

### 1.6 POST — Verify Payment (Admin Only)
`POST /api/enrollments/verify-payment`

Admin approves or rejects a payment for an enrollment application.

**Required Headers:**

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer <admin_jwt_token>` |
| `X-User-Id` | Admin's user ID |
| `X-User-Role` | Must be `admin` |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enrollmentId` | number | Yes | ID of the enrollment to verify |
| `action` | string | Yes | `APPROVE` or `REJECT` |
| `rejectionReason` | string | No | Required only when action is `REJECT` |

```bash
# Approve a payment
curl -X POST "http://localhost:8083/api/enrollments/verify-payment" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-User-Id: admin1" \
  -H "X-User-Role: admin" \
  -d '{
    "enrollmentId": 5,
    "action": "APPROVE"
  }'

# Reject a payment with a reason
curl -X POST "http://localhost:8083/api/enrollments/verify-payment" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "X-User-Id: admin1" \
  -H "X-User-Role: admin" \
  -d '{
    "enrollmentId": 5,
    "action": "REJECT",
    "rejectionReason": "Payment screenshot was unclear"
  }'
```

**Success Response (200):** Returns updated enrollment data.

**Error Response (403):** Caller is not an admin.

---

### 1.7 POST — Bulk Assign Courses (Admin Only)
`POST /api/enrollments/bulk-assign`

Admin assigns a student to multiple courses at once (e.g., after offline payment).

**Required Headers:**

| Header | Value |
|--------|-------|
| `X-User-Role` | Must be `admin` |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string | Yes | The student's ID |
| `courseIds` | array of numbers | Yes | List of course IDs to enroll in |

```bash
curl -X POST "http://localhost:8083/api/enrollments/bulk-assign" \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "userId": "student123",
    "courseIds": [10, 11, 12]
  }'
```

**Success Response (200):**
```json
{
  "success": true,
  "enrollments": [
    { "id": 6, "studentId": "student123", "courseId": 10, "progress": 0, "enrolledAt": "2026-04-17T09:30:00", "completedAt": null },
    { "id": 7, "studentId": "student123", "courseId": 11, "progress": 0, "enrolledAt": "2026-04-17T09:30:00", "completedAt": null },
    { "id": 8, "studentId": "student123", "courseId": 12, "progress": 0, "enrolledAt": "2026-04-17T09:30:00", "completedAt": null }
  ]
}
```

**Error Response (400):** `courseIds` list is missing.  
**Error Response (403):** Caller is not an admin.

---

## 2. Enrollment Forms

**Base path:** `/api/enrollments/forms`

Admin creates and manages the application forms that students fill out to enroll in a course.

---

### 2.1 GET — List All Forms
`GET /api/enrollments/forms`

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `view` | No | Pass `trash` to see soft-deleted forms |

```bash
# Get all active forms
curl -X GET "http://localhost:8083/api/enrollments/forms"

# Get trashed (deleted) forms
curl -X GET "http://localhost:8083/api/enrollments/forms?view=trash"
```

**Success Response (200):**
```json
[
  {
    "id": "form_abc123",
    "title": "Cybersecurity Fundamentals Application",
    "courseId": 10,
    "active": true,
    "description": "Fill this form to apply for the course.",
    "fields": [
      { "name": "phone", "label": "Phone Number", "type": "text", "required": true },
      { "name": "education", "label": "Highest Education", "type": "select", "options": ["10th","12th","Graduate"], "required": true }
    ],
    "startTime": "2026-04-01T00:00:00",
    "endTime": "2026-05-01T00:00:00",
    "quiz": false,
    "limitOneResponse": true,
    "deletedAt": null
  }
]
```

---

### 2.2 GET — Get Single Form
`GET /api/enrollments/forms/{id}`

**Path Parameter:** `id` — the form ID (string)

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `token` | No | Access token for password-protected forms |

```bash
# Public form
curl -X GET "http://localhost:8083/api/enrollments/forms/form_abc123"

# Private/token-protected form
curl -X GET "http://localhost:8083/api/enrollments/forms/form_abc123?token=mySecretToken"
```

**Success Response (200):** Single form object (same structure as list above).  
**Error Response (404):** Form not found.

---

### 2.3 POST — Create Form
`POST /api/enrollments/forms`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Form title |
| `courseId` | number | No | Associated course |
| `description` | string | No | Form description |
| `active` | boolean | Yes | Whether form accepts responses |
| `fields` | array | Yes | Form field definitions |
| `quiz` | boolean | No | Whether form is a quiz |
| `quizSettings` | object | No | Quiz configuration |
| `limitOneResponse` | boolean | No | Prevent duplicate submissions per email |
| `startTime` | string (ISO) | No | Form opens at this time |
| `endTime` | string (ISO) | No | Form closes at this time |

```bash
curl -X POST "http://localhost:8083/api/enrollments/forms" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "AI Masterclass Application",
    "courseId": 15,
    "description": "Apply for our AI Masterclass course",
    "active": true,
    "limitOneResponse": true,
    "startTime": "2026-04-17T00:00:00",
    "endTime": "2026-05-17T00:00:00",
    "fields": [
      {
        "name": "phone",
        "label": "Phone Number",
        "type": "text",
        "required": true
      },
      {
        "name": "education",
        "label": "Highest Education",
        "type": "select",
        "options": ["10th", "12th", "Graduate", "Post Graduate"],
        "required": true
      },
      {
        "name": "experience",
        "label": "Do you have programming experience?",
        "type": "radio",
        "options": ["Yes", "No"],
        "required": true
      }
    ]
  }'
```

**Success Response (200):** Created form object with generated `id`.

---

### 2.4 PUT — Update Form
`PUT /api/enrollments/forms/{id}`

Replaces form fields. Send the complete updated form object.

```bash
curl -X PUT "http://localhost:8083/api/enrollments/forms/form_abc123" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Course Application",
    "courseId": 15,
    "description": "Updated description",
    "active": true,
    "limitOneResponse": true,
    "fields": [
      { "name": "phone", "label": "Phone Number", "type": "text", "required": true }
    ]
  }'
```

**Success Response (200):** Updated form object.  
**Error Response (404):** Form not found.

---

### 2.5 DELETE — Delete Form
`DELETE /api/enrollments/forms/{id}`

**Query Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `permanent` | No | `false` | `true` = hard delete, `false` = soft delete (moves to trash) |

```bash
# Soft delete (recommended — moves to trash, can be restored)
curl -X DELETE "http://localhost:8083/api/enrollments/forms/form_abc123"

# Permanent delete (cannot be undone)
curl -X DELETE "http://localhost:8083/api/enrollments/forms/form_abc123?permanent=true"
```

**Success Response (200):**
```json
{ "success": true }
```

---

### 2.6 POST — Restore Form from Trash
`POST /api/enrollments/forms/{id}/restore`

```bash
curl -X POST "http://localhost:8083/api/enrollments/forms/form_abc123/restore"
```

**Success Response (200):**
```json
{ "success": true }
```

---

### 2.7 PATCH — Toggle Form Active Status
`PATCH /api/enrollments/forms/{id}/active`

Enable or disable a form (open/close applications).

```bash
# Disable form (stop accepting responses)
curl -X PATCH "http://localhost:8083/api/enrollments/forms/form_abc123/active" \
  -H "Content-Type: application/json" \
  -d '{ "active": false }'

# Enable form
curl -X PATCH "http://localhost:8083/api/enrollments/forms/form_abc123/active" \
  -H "Content-Type: application/json" \
  -d '{ "active": true }'
```

**Success Response (200):**
```json
{ "success": true }
```

---

### 2.8 POST — Duplicate Form
`POST /api/enrollments/forms/{id}/duplicate`

Creates a copy of the form with a new ID.

```bash
curl -X POST "http://localhost:8083/api/enrollments/forms/form_abc123/duplicate"
```

**Success Response (200):** New form object (copy of original with a new `id`).

---

## 3. Form Responses

**Base path:** `/api/enrollments/responses`

A response is created each time a student submits an enrollment form. This is the primary model for new enrollment flows.

---

### 3.1 GET — List All Responses
`GET /api/enrollments/responses`

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `view` | No | Pass `trash` to see soft-deleted responses |

```bash
# All active responses
curl -X GET "http://localhost:8083/api/enrollments/responses"

# Trashed responses
curl -X GET "http://localhost:8083/api/enrollments/responses?view=trash"
```

**Success Response (200):**
```json
{
  "success": true,
  "responses": [
    {
      "id": 101,
      "formId": "form_abc123",
      "studentEmail": "rahul@example.com",
      "studentData": "{\"phone\":\"9876543210\",\"education\":\"Graduate\",\"name\":\"Rahul Singh\"}",
      "paymentStatus": "PENDING",
      "transactionId": null,
      "amountPaid": null,
      "submittedAt": "2026-04-17T10:00:00",
      "deletedAt": null
    }
  ]
}
```

> **Note:** `studentData` is a JSON string. Parse it with `JSON.parse(response.studentData)` on the frontend.

---

### 3.2 GET — Check if Student Already Responded
`GET /api/enrollments/responses/check`

**Use this before showing the form** to prevent duplicate submissions.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `formId` | Yes | The form ID |
| `email` | Yes | Student's email |

```bash
curl -X GET "http://localhost:8083/api/enrollments/responses/check?formId=form_abc123&email=rahul@example.com"
```

**Success Response (200):**
```json
{ "alreadyResponded": false }
```

```json
{ "alreadyResponded": true }
```

---

### 3.3 POST — Submit Form Response
`POST /api/enrollments/responses`

Called when a student submits the enrollment application form.

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `formId` | string | Yes | The form being submitted |
| `studentEmail` | string | Yes | Student's email |
| `studentData` | string (JSON) | Yes | Stringified field values from the form |

```bash
curl -X POST "http://localhost:8083/api/enrollments/responses" \
  -H "Content-Type: application/json" \
  -d '{
    "formId": "form_abc123",
    "studentEmail": "rahul@example.com",
    "studentData": "{\"phone\":\"9876543210\",\"education\":\"Graduate\",\"name\":\"Rahul Singh\"}"
  }'
```

**Success Response (200):**
```json
{
  "id": 101,
  "formId": "form_abc123",
  "studentEmail": "rahul@example.com",
  "studentData": "{\"phone\":\"9876543210\",\"education\":\"Graduate\",\"name\":\"Rahul Singh\"}",
  "paymentStatus": "PENDING",
  "transactionId": null,
  "amountPaid": null,
  "submittedAt": "2026-04-17T10:00:00"
}
```

> **Save the `id`** from this response — you'll need it for initiating payment (`enrollmentFormResponseId`).

---

### 3.4 POST — Bulk Submit Responses
`POST /api/enrollments/responses/bulk`

Submit multiple student responses at once (import use case).

```bash
curl -X POST "http://localhost:8083/api/enrollments/responses/bulk" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "formId": "form_abc123",
      "studentData": { "name": "Rahul Singh", "phone": "9876543210" }
    },
    {
      "formId": "form_abc123",
      "studentData": { "name": "Priya Sharma", "phone": "9123456789" }
    }
  ]'
```

**Success Response (200):** Array of created response objects.

---

### 3.5 POST — Finalize Response (Mark as Paid)
`POST /api/enrollments/responses/{id}/finalize`

Called after a successful PayU payment to mark the response as paid and trigger admin notification.

**Path Parameter:** `id` — the response ID

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `transactionId` | string | Yes | PayU transaction ID |
| `paymentTxnid` | string | No | Alias for transactionId |
| `amount` | number | Yes | Amount paid |

```bash
curl -X POST "http://localhost:8083/api/enrollments/responses/101/finalize" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN1713340800000",
    "amount": 5000
  }'
```

**Success Response (200):**
```json
{
  "id": 101,
  "formId": "form_abc123",
  "studentEmail": "rahul@example.com",
  "paymentStatus": "PAID",
  "transactionId": "TXN1713340800000",
  "amountPaid": 5000.0,
  "submittedAt": "2026-04-17T10:00:00"
}
```

---

### 3.6 PUT — Update Response by ID
`PUT /api/enrollments/responses/{id}`

Update student data, payment status, or transaction ID on an existing response.

```bash
curl -X PUT "http://localhost:8083/api/enrollments/responses/101" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentStatus": "PAID",
    "transactionId": "TXN1713340800000",
    "studentData": { "phone": "9876543210", "education": "Graduate" }
  }'
```

**Success Response (200):** Updated response object.

---

### 3.7 PUT — Update Response by Transaction ID
`PUT /api/enrollments/responses/by-txn/{txnid}`

Update a response using the PayU transaction ID (used in payment webhooks/callbacks).

```bash
curl -X PUT "http://localhost:8083/api/enrollments/responses/by-txn/TXN1713340800000" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentStatus": "PAID",
    "transactionId": "TXN1713340800000"
  }'
```

**Success Response (200):** Updated response object.  
**Error Response (404):** No response found for that transaction ID.

---

### 3.8 DELETE — Delete Response
`DELETE /api/enrollments/responses/{id}`

**Query Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `permanent` | No | `false` | Hard delete if `true` |
| `legacy` | No | `false` | Use legacy submission table if `true` |

```bash
# Soft delete
curl -X DELETE "http://localhost:8083/api/enrollments/responses/101"

# Permanent delete
curl -X DELETE "http://localhost:8083/api/enrollments/responses/101?permanent=true"
```

**Success Response (200):**
```json
{ "success": true }
```

---

### 3.9 POST — Restore Response
`POST /api/enrollments/responses/{id}/restore`

**Query Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `legacy` | No | `false` | Use legacy submission table if `true` |

```bash
curl -X POST "http://localhost:8083/api/enrollments/responses/101/restore"
```

**Success Response (200):**
```json
{ "success": true }
```

---

## 4. Submissions

**Base path:** `/api/enrollments/submissions`

A simpler/legacy model for enrollment applications. Used in the admin "Applications" panel. May exist alongside the newer Responses model.

---

### 4.1 GET — List Submissions
`GET /api/enrollments/submissions`

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `status` | No | Filter by status: `PENDING`, `VERIFIED`, `REJECTED` |

```bash
# All active submissions
curl -X GET "http://localhost:8083/api/enrollments/submissions"

# Only pending submissions
curl -X GET "http://localhost:8083/api/enrollments/submissions?status=PENDING"

# Only verified (approved)
curl -X GET "http://localhost:8083/api/enrollments/submissions?status=VERIFIED"

# Only rejected
curl -X GET "http://localhost:8083/api/enrollments/submissions?status=REJECTED"
```

**Success Response (200):**
```json
[
  {
    "id": 201,
    "fullName": "Rahul Singh",
    "email": "rahul@example.com",
    "phone": "9876543210",
    "studentEmail": "rahul@example.com",
    "courseId": 10,
    "amountPaid": 4999.0,
    "transactionId": "TXN1713340800000",
    "screenshotUrl": null,
    "studentData": null,
    "paymentStatus": "PENDING",
    "status": "PENDING",
    "reviewedBy": null,
    "reviewedAt": null,
    "rejectionReason": null,
    "createdUserId": null,
    "deletedAt": null,
    "createdAt": "2026-04-17T10:00:00"
  }
]
```

---

### 4.2 POST — Create Submission
`POST /api/enrollments/submissions`

Creates a new enrollment application. Status is automatically set to `PENDING`.

```bash
curl -X POST "http://localhost:8083/api/enrollments/submissions" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Rahul Singh",
    "email": "rahul@example.com",
    "phone": "9876543210",
    "studentEmail": "rahul@example.com",
    "courseId": 10,
    "amountPaid": 4999,
    "transactionId": "TXN1713340800000"
  }'
```

**Success Response (200):**
```json
{
  "id": 201,
  "fullName": "Rahul Singh",
  "email": "rahul@example.com",
  "phone": "9876543210",
  "studentEmail": "rahul@example.com",
  "courseId": 10,
  "amountPaid": 4999.0,
  "transactionId": "TXN1713340800000",
  "paymentStatus": "PENDING",
  "status": "PENDING",
  "createdAt": "2026-04-17T10:00:00"
}
```

---

### 4.3 PATCH — Approve or Reject Submission (Admin)
`PATCH /api/enrollments/submissions/{id}/status`

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `X-User-Id` | No | Admin's user ID (recorded as reviewer) |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | Yes | `PENDING`, `VERIFIED`, or `REJECTED` |
| `rejectionReason` | string | No | Required when status is `REJECTED` |

```bash
# Approve (VERIFIED)
curl -X PATCH "http://localhost:8083/api/enrollments/submissions/201/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin1" \
  -d '{ "status": "VERIFIED" }'

# Reject with reason
curl -X PATCH "http://localhost:8083/api/enrollments/submissions/201/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin1" \
  -d '{
    "status": "REJECTED",
    "rejectionReason": "Incomplete documents provided"
  }'
```

**Success Response (200):**
```json
{ "success": true, "status": "VERIFIED" }
```

**Error Response (404):** Submission not found.

---

### 4.4 DELETE — Soft Delete Submission
`DELETE /api/enrollments/submissions/{id}`

```bash
curl -X DELETE "http://localhost:8083/api/enrollments/submissions/201"
```

**Success Response (200):**
```json
{ "success": true }
```

---

## 5. Workflows

**Base path:** `/api/enrollments/workflows`

Defines the multi-step process a student must complete to get enrolled (e.g., submit → pay → admin confirms). A default "Standard Enrollment" workflow is auto-created on service startup.

---

### 5.1 GET — List All Workflows
`GET /api/enrollments/workflows`

```bash
curl -X GET "http://localhost:8083/api/enrollments/workflows"
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "name": "Standard Enrollment",
    "description": "Default enrollment process for all courses",
    "autoApprove": false,
    "paymentRequired": true,
    "depositAmount": 500.0,
    "isDefault": true,
    "isActive": true,
    "courseId": null,
    "steps": {
      "1": "Submit Application",
      "2": "Document Verification",
      "3": "Payment Processing",
      "4": "Enrollment Confirmation"
    },
    "requiredDocuments": null,
    "approvalChain": null,
    "createdAt": "2026-04-17T09:00:00",
    "updatedAt": "2026-04-17T09:00:00"
  }
]
```

---

### 5.2 GET — Get Single Workflow
`GET /api/enrollments/workflows/{id}`

```bash
curl -X GET "http://localhost:8083/api/enrollments/workflows/1"
```

**Success Response (200):** Single workflow object.  
**Error Response (404):** Workflow not found.

---

### 5.3 POST — Create Workflow
`POST /api/enrollments/workflows`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Workflow name |
| `description` | string | No | Description |
| `autoApprove` | boolean | No | Auto-enroll after payment if `true` |
| `paymentRequired` | boolean | No | Whether payment is needed |
| `depositAmount` | number | No | Amount in INR |
| `steps` | object | No | Step number -> step name map |
| `isActive` | boolean | No | Whether workflow is usable |
| `courseId` | number | No | Restrict to a specific course |

```bash
curl -X POST "http://localhost:8083/api/enrollments/workflows" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Fast Track Enrollment",
    "description": "Auto-approve after payment confirmation",
    "autoApprove": true,
    "paymentRequired": true,
    "depositAmount": 1000.0,
    "isActive": true,
    "steps": {
      "1": "Submit Application",
      "2": "Pay Fee",
      "3": "Access Granted"
    }
  }'
```

**Success Response (200):** Created workflow object.

---

### 5.4 PUT — Update Workflow
`PUT /api/enrollments/workflows/{id}`

```bash
curl -X PUT "http://localhost:8083/api/enrollments/workflows/1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Standard Enrollment (Updated)",
    "description": "Updated process",
    "autoApprove": false,
    "paymentRequired": true,
    "depositAmount": 750.0,
    "isActive": true,
    "steps": {
      "1": "Submit Form",
      "2": "Pay Deposit",
      "3": "Admin Review",
      "4": "Confirm Enrollment"
    }
  }'
```

**Success Response (200):** Updated workflow object.  
**Error Response (404):** Workflow not found.

---

### 5.5 DELETE — Delete Workflow
`DELETE /api/enrollments/workflows/{id}`

```bash
curl -X DELETE "http://localhost:8083/api/enrollments/workflows/2"
```

**Success Response (200):** Empty `200 OK`.

---

### 5.6 PATCH — Set as Default Workflow
`PATCH /api/enrollments/workflows/{id}/default`

Removes the default flag from all other workflows and sets this one as default.

```bash
curl -X PATCH "http://localhost:8083/api/enrollments/workflows/2/default"
```

**Success Response (200):** Updated workflow object with `"isDefault": true`.

---

## 6. Payment (PayU)

**Base path:** `/api/payu-payment`

Handles PayU payment gateway integration for course enrollment fees.

---

### 6.1 POST — Initiate Payment
`POST /api/payu-payment`

Generates the payment hash and all required data to post to PayU's payment page.

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `amount` | number | Yes | Fee amount in INR (e.g., `5000`) |
| `courseName` | string | Yes | Shown as product info on PayU |
| `studentName` | string | Yes | Student's first name |
| `studentEmail` | string | Yes | Student's email |
| `studentPhone` | string | Yes | Student's phone (10 digits) |
| `formId` | string | Yes | The form ID (used in redirect URLs) |
| `enrollmentFormResponseId` | number | Yes | The response ID returned after form submission |

```bash
curl -X POST "http://localhost:8083/api/payu-payment" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 5000,
    "courseName": "Cybersecurity Fundamentals",
    "studentName": "Rahul Singh",
    "studentEmail": "rahul@example.com",
    "studentPhone": "9876543210",
    "formId": "form_abc123",
    "enrollmentFormResponseId": 101
  }'
```

**Success Response (200):**
```json
{
  "success": true,
  "paymentData": {
    "key": "your_merchant_key",
    "txnid": "TXN1713340800000",
    "amount": "5000",
    "productinfo": "Cybersecurity Fundamentals",
    "firstname": "Rahul Singh",
    "email": "rahul@example.com",
    "phone": "9876543210",
    "surl": "http://localhost:3000/enroll-form.html?status=success&formId=form_abc123&txnid=TXN1713340800000&email=rahul@example.com&responseId=101",
    "furl": "http://localhost:3000/enroll-form.html?status=failure&formId=form_abc123&txnid=TXN1713340800000&email=rahul@example.com&responseId=101",
    "hash": "abc123def456...sha512hash...789xyz",
    "action": "https://secure.payu.in/_payment"
  }
}
```

**Frontend Usage:**
```html
<!-- Create a hidden form and submit it programmatically -->
<form id="payuForm" method="POST" action="https://secure.payu.in/_payment">
  <!-- Populate hidden inputs from paymentData fields -->
</form>
```
```javascript
const data = response.paymentData;
const form = document.getElementById('payuForm');
for (const [key, value] of Object.entries(data)) {
  if (key !== 'action') {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = key;
    input.value = value;
    form.appendChild(input);
  }
}
form.submit(); // Redirects to PayU payment page
```

**Error Response (500):**
```json
{
  "success": false,
  "message": "Payment initialization failed: <error details>"
}
```

---

## 7. Admin Stats

**Base path:** `/api/admin/stats`

Revenue and enrollment statistics for the admin dashboard.

---

### 7.1 GET — Revenue Stats (Admin Only)
`GET /api/admin/stats/revenue`

**Required Headers:**

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer <admin_jwt_token>` |

```bash
curl -X GET "http://localhost:8083/api/admin/stats/revenue" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

**Success Response (200):**
```json
{
  "totalRevenue": 125000.0,
  "paidOrders": 25,
  "totalEnrollments": 42
}
```

**Error Response (403):** Not authorized (must have ADMIN role).

---

## 8. Complete Enrollment Flow

This is the recommended step-by-step flow for a student enrolling in a course:

```
Step 1: Load Form
  GET /api/enrollments/forms/{formId}
  → Display the form fields to the student

Step 2: Check Duplicate
  GET /api/enrollments/responses/check?formId={id}&email={email}
  → If alreadyResponded: true → show "You have already applied" message

Step 3: Student Submits Form
  POST /api/enrollments/responses
  → Save the response ID returned (e.g., id: 101)

Step 4: Initiate Payment
  POST /api/payu-payment
  → Get paymentData, submit hidden form to PayU

Step 5a: Payment SUCCESS (on return to surl)
  POST /api/enrollments/responses/{responseId}/finalize
  → Mark response as PAID, admin gets notified

Step 5b: Payment FAILURE (on return to furl)
  → Show failure message, offer retry

Step 6: Admin Reviews & Approves (if autoApprove is false)
  POST /api/enrollments/verify-payment  (action: APPROVE)
  → This creates the actual Enrollment record

Step 7: Student Accesses Course
  GET /api/enrollments?studentId={id}  (X-User-Role: student)
  → Returns their enrolled courses with progress

Step 8: Track Progress (as student learns)
  PATCH /api/enrollments/progress
  → Update progress as student completes lessons
```

---

## 9. Error Reference

| Status | Meaning | When it Happens |
|--------|---------|-----------------|
| `200` | OK | Request succeeded |
| `201` | Created | New resource created (enrollment) |
| `400` | Bad Request | Missing required fields (e.g., courseIds) |
| `403` | Forbidden | Wrong role, or teacher not assigned to course |
| `404` | Not Found | Resource doesn't exist (enrollment, form, response) |
| `500` | Server Error | Internal error (e.g., payment hash failure) |

---

## Quick Reference — All 36 Endpoints

| # | Method | Path | Who Can Call | Purpose |
|---|--------|------|-------------|---------|
| 1 | GET | `/api/enrollments/config` | Anyone | Get form config for a formId |
| 2 | GET | `/api/enrollments` | Student/Admin/Teacher | Fetch enrollments (role-based) |
| 3 | POST | `/api/enrollments` | Admin/Backend | Create enrollment directly |
| 4 | PUT | `/api/enrollments/{id}` | Student/Admin | Update progress by enrollment ID |
| 5 | PATCH | `/api/enrollments/progress` | Student/Admin | Update progress by student+course |
| 6 | POST | `/api/enrollments/verify-payment` | Admin only | Approve/reject payment |
| 7 | POST | `/api/enrollments/bulk-assign` | Admin only | Enroll student in multiple courses |
| 8 | GET | `/api/enrollments/forms` | Admin | List all forms |
| 9 | GET | `/api/enrollments/forms/{id}` | Anyone | Get single form |
| 10 | POST | `/api/enrollments/forms` | Admin | Create new form |
| 11 | PUT | `/api/enrollments/forms/{id}` | Admin | Update form |
| 12 | DELETE | `/api/enrollments/forms/{id}` | Admin | Delete form (soft/hard) |
| 13 | POST | `/api/enrollments/forms/{id}/restore` | Admin | Restore deleted form |
| 14 | PATCH | `/api/enrollments/forms/{id}/active` | Admin | Enable/disable form |
| 15 | POST | `/api/enrollments/forms/{id}/duplicate` | Admin | Duplicate form |
| 16 | GET | `/api/enrollments/responses` | Admin | List all responses |
| 17 | GET | `/api/enrollments/responses/check` | Student | Check duplicate submission |
| 18 | POST | `/api/enrollments/responses` | Student | Submit enrollment form |
| 19 | POST | `/api/enrollments/responses/bulk` | Admin | Import multiple responses |
| 20 | POST | `/api/enrollments/responses/{id}/finalize` | Student/System | Mark response as paid |
| 21 | PUT | `/api/enrollments/responses/{id}` | Admin | Update response |
| 22 | PUT | `/api/enrollments/responses/by-txn/{txnid}` | System (webhook) | Update response by txn ID |
| 23 | DELETE | `/api/enrollments/responses/{id}` | Admin | Delete response |
| 24 | POST | `/api/enrollments/responses/{id}/restore` | Admin | Restore response |
| 25 | GET | `/api/enrollments/submissions` | Admin | List submissions |
| 26 | POST | `/api/enrollments/submissions` | Student | Create submission |
| 27 | PATCH | `/api/enrollments/submissions/{id}/status` | Admin | Approve/reject submission |
| 28 | DELETE | `/api/enrollments/submissions/{id}` | Admin | Soft delete submission |
| 29 | GET | `/api/enrollments/workflows` | Admin | List all workflows |
| 30 | GET | `/api/enrollments/workflows/{id}` | Admin | Get single workflow |
| 31 | POST | `/api/enrollments/workflows` | Admin | Create workflow |
| 32 | PUT | `/api/enrollments/workflows/{id}` | Admin | Update workflow |
| 33 | DELETE | `/api/enrollments/workflows/{id}` | Admin | Delete workflow |
| 34 | PATCH | `/api/enrollments/workflows/{id}/default` | Admin | Set as default workflow |
| 35 | POST | `/api/payu-payment` | Student | Initiate PayU payment |
| 36 | GET | `/api/admin/stats/revenue` | Admin | Get revenue stats |
