# Form Service — Frontend API Guide

**Service:** form-service  
**Base URL:** `http://localhost:8087`  
**API Prefix:** `/api/forms`  
**Database:** `cyberlearnix_forms` (PostgreSQL, port 5999)  
**Total Endpoints:** 15  

---

## Who Uses What — Quick Role Map

| Who | Can Do | Section |
|-----|--------|---------|
| **Admin Panel** | Create/edit/delete/restore forms, view all responses, analytics, CSV export, manage quiz settings | Sections 2–10 |
| **Teacher** | No dedicated form APIs — uses public form submission same as students | Section 11–12 |
| **Student** | View a specific form, check if already responded, submit a response | Sections 11–12 |
| **Public (no login)** | Get form by ID, get form via token link, submit, check if already responded | Sections 11–12 |

---

## Table of Contents

1. [Service Overview](#1-service-overview)
2. [Admin — List All Forms](#2-admin--list-all-forms)
3. [Admin — Get Single Form (Admin View)](#3-admin--get-single-form-admin-view)
4. [Admin — Create Form](#4-admin--create-form)
5. [Admin — Update Form](#5-admin--update-form)
6. [Admin — Toggle Form Active/Inactive](#6-admin--toggle-form-activeinactive)
7. [Admin — Duplicate Form](#7-admin--duplicate-form)
8. [Admin — Delete Form (Soft Delete)](#8-admin--delete-form-soft-delete)
9. [Admin — Restore Deleted Form](#9-admin--restore-deleted-form)
10. [Admin — Responses & Analytics](#10-admin--responses--analytics)
11. [Public / Student — Get Form](#11-public--student--get-form)
12. [Public / Student — Submit Response](#12-public--student--submit-response)
13. [Public — Check if Already Responded](#13-public--check-if-already-responded)
14. [Field Types Reference](#14-field-types-reference)
15. [Error Reference](#15-error-reference)
16. [Quick Reference — All Endpoints](#16-quick-reference--all-endpoints)

---

## 1. Service Overview

The form service is a **general-purpose form builder and submission system**. It supports:

- **Regular forms** — contact forms, registration forms, feedback, surveys
- **Quiz forms** — scored forms with correct answers, time limits, and pass scores
- **Access control** — public (open link), token-protected (private link), time-windowed (only accept between `startTime` and `endTime`)
- **One-response limit** — optionally block re-submissions from same email
- **Analytics** — per-question aggregation (counts for choice questions, recent answers for text)
- **CSV export** — download all responses as a spreadsheet

**Auth model:**

| Endpoint Category | Auth Required |
|------------------|--------------|
| Admin CRUD (create/edit/delete/restore/duplicate) | JWT + `ADMIN` role |
| Admin read (list forms, view responses, analytics, export) | JWT + `ADMIN` role |
| Public/student get form | None (token optional for private forms) |
| Public/student submit response | None |
| Public check already-responded | None |

**Common Admin Headers:**

| Header | Value |
|--------|-------|
| `X-User-Role` | `admin` |
| `X-User-Id` | Your admin UUID |
| `Authorization` | `Bearer <jwt_token>` |
| `Content-Type` | `application/json` (for POST/PUT) |

---

## 2. Admin — List All Forms

`GET /api/forms`

Returns a list of all forms. Supports switching between active forms and trash (soft-deleted).

**Required Auth:** Admin JWT

**Query Parameters:**

| Parameter | Required | Default | Values | Description |
|-----------|----------|---------|--------|-------------|
| `view` | No | `active` | `active`, `trash` | `active` = live forms (not deleted). `trash` = soft-deleted forms awaiting permanent delete or restore. |

```bash
# Get all active (live) forms
curl http://localhost:8087/api/forms \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"

# Get all trashed (soft-deleted) forms
curl "http://localhost:8087/api/forms?view=trash" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "title": "Student Registration Form",
    "description": "Fill this to register for Batch 4",
    "fields": [
      { "id": "field_1", "label": "Full Name", "field_type": "text", "is_required": true },
      { "id": "field_2", "label": "Email", "field_type": "email", "is_required": true },
      { "id": "field_3", "label": "Phone", "field_type": "number", "is_required": false }
    ],
    "isActive": true,
    "token": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "startTime": "2026-04-01T00:00:00",
    "endTime": "2026-06-30T23:59:59",
    "isQuiz": false,
    "quizSettings": null,
    "limitOneResponse": true,
    "createdBy": "admin-uuid",
    "createdAt": "2026-03-15T10:30:00",
    "updatedAt": "2026-04-01T09:00:00"
  }
]
```

**Empty list:**
```json
[]
```

---

## 3. Admin — Get Single Form (Admin View)

`GET /api/forms/{id}`  
*(with valid admin JWT — returns full form including inactive/deleted)*

> **Same endpoint is used by public** with or without token (see Section 11). When called with an **admin JWT**, the service returns the admin-grade view.

```bash
curl http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):** Full form object (same shape as Section 2 responses).

**Error (404):** Form not found or already permanently deleted:
```json
{ "error": "Form not found" }
```

---

## 4. Admin — Create Form

`POST /api/forms`

Creates a new form. The `fields` array defines the questions/inputs. See [Section 14](#14-field-types-reference) for all supported field types.

**Required Auth:** Admin JWT

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Form title shown at top |
| `description` | string | No | Subtitle/instructions |
| `fields` | array | Yes | Array of field definition objects (see Section 14) |
| `isActive` | boolean | No | Defaults to `true`. Set `false` to save as draft. |
| `startTime` | string (ISO 8601) | No | Form is NOT accessible before this time |
| `endTime` | string (ISO 8601) | No | Form stops accepting responses after this time |
| `isQuiz` | boolean | No | Set `true` to enable quiz mode (scored responses) |
| `quizSettings` | object | No | Required if `isQuiz: true` — quiz config object |
| `limitOneResponse` | boolean | No | Defaults to `false`. If `true`, one email can only submit once. |

**`quizSettings` Object:**
```json
{
  "timeLimitMinutes": 30,
  "passingScore": 70,
  "showResults": true,
  "showCorrectAnswers": false
}
```

---

### 4.1 Example — Create a Regular Form

```bash
curl -X POST http://localhost:8087/api/forms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Student Registration Form",
    "description": "Please fill in your details to register for Batch 4",
    "isActive": true,
    "limitOneResponse": true,
    "startTime": "2026-04-17T00:00:00",
    "endTime": "2026-06-30T23:59:59",
    "fields": [
      {
        "id": "field_1",
        "label": "Full Name",
        "field_type": "text",
        "is_required": true,
        "placeholder": "Enter your full name"
      },
      {
        "id": "field_2",
        "label": "Email Address",
        "field_type": "email",
        "is_required": true,
        "placeholder": "your@email.com"
      },
      {
        "id": "field_3",
        "label": "Phone Number",
        "field_type": "number",
        "is_required": true
      },
      {
        "id": "field_4",
        "label": "Which course are you interested in?",
        "field_type": "dropdown",
        "is_required": true,
        "options": ["Cybersecurity Fundamentals", "Ethical Hacking", "Network Security", "AI & ML"]
      },
      {
        "id": "field_5",
        "label": "How did you hear about us?",
        "field_type": "radio",
        "is_required": false,
        "options": ["Google", "Social Media", "Friend/Colleague", "Other"]
      },
      {
        "id": "field_6",
        "label": "Additional Message",
        "field_type": "textarea",
        "is_required": false,
        "placeholder": "Any specific queries?"
      },
      {
        "id": "field_7",
        "label": "I agree to the terms and conditions",
        "field_type": "declaration_terms",
        "is_required": true
      }
    ]
  }'
```

---

### 4.2 Example — Create a Quiz Form

```bash
curl -X POST http://localhost:8087/api/forms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Module 1 Assessment Quiz",
    "description": "Test your understanding of cybersecurity basics",
    "isActive": true,
    "isQuiz": true,
    "limitOneResponse": true,
    "quizSettings": {
      "timeLimitMinutes": 20,
      "passingScore": 70,
      "showResults": true,
      "showCorrectAnswers": false
    },
    "fields": [
      {
        "id": "q1",
        "label": "What does OSI stand for?",
        "field_type": "radio",
        "is_required": true,
        "points": 2,
        "options": [
          "Open Systems Interconnection",
          "Open Software Interface",
          "Operating System Integration"
        ],
        "correct_answer": "Open Systems Interconnection"
      },
      {
        "id": "q2",
        "label": "Which layer handles IP addressing?",
        "field_type": "dropdown",
        "is_required": true,
        "points": 2,
        "options": ["Layer 1 - Physical", "Layer 2 - Data Link", "Layer 3 - Network", "Layer 4 - Transport"],
        "correct_answer": "Layer 3 - Network"
      },
      {
        "id": "q3",
        "label": "TCP is connection-oriented",
        "field_type": "radio",
        "is_required": true,
        "points": 1,
        "options": ["True", "False"],
        "correct_answer": "True"
      },
      {
        "id": "q4",
        "label": "Rate the difficulty of this quiz",
        "field_type": "star_rating",
        "is_required": false,
        "options": ["5"]
      }
    ]
  }'
```

**Success Response (201):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "title": "Module 1 Assessment Quiz",
  "description": "Test your understanding of cybersecurity basics",
  "fields": [ ... ],
  "isActive": true,
  "token": "z9y8x7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4",
  "startTime": null,
  "endTime": null,
  "isQuiz": true,
  "quizSettings": {
    "timeLimitMinutes": 20,
    "passingScore": 70,
    "showResults": true,
    "showCorrectAnswers": false
  },
  "limitOneResponse": true,
  "createdBy": "admin-uuid",
  "createdAt": "2026-04-17T10:00:00",
  "updatedAt": "2026-04-17T10:00:00"
}
```

---

## 5. Admin — Update Form

`PUT /api/forms/{id}`

Updates any field of an existing form. Always send the **full object** (partial updates are not supported — missing fields will be overwritten with null/defaults).

**Required Auth:** Admin JWT

**Request Body:** Same as Create Form (Section 4).

```bash
curl -X PUT http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Student Registration Form — Batch 5",
    "description": "Updated for Batch 5 registration",
    "isActive": true,
    "limitOneResponse": true,
    "endTime": "2026-07-31T23:59:59",
    "fields": [ ... ]
  }'
```

**Success Response (200):** Updated form object.  
**Error (404):** Form not found.

---

## 6. Admin — Toggle Form Active/Inactive

`PATCH /api/forms/{id}/active`

Quickly enable or disable a form without a full update. Useful for opening/closing a form on demand.

**Required Auth:** Admin JWT

**Request Body:**
```json
{ "active": true }
```
or
```json
{ "active": false }
```

```bash
# Deactivate a form (stop accepting responses)
curl -X PATCH http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/active \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -d '{ "active": false }'

# Re-activate a form
curl -X PATCH http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/active \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -d '{ "active": true }'
```

**Success Response (200):**
```json
{ "message": "Form status updated" }
```

**Error (404):**
```json
{ "error": "Form not found" }
```

---

## 7. Admin — Duplicate Form

`POST /api/forms/{id}/duplicate`

Creates an exact copy of an existing form. The duplicate:
- Gets a new unique `id` and `token`
- Has `" (Copy)"` appended to the title
- Is set to `isActive: false` (draft — won't accept responses until activated)

**Required Auth:** Admin JWT

```bash
curl -X POST http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/duplicate \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (201):**
```json
{
  "id": "new-uuid-here",
  "title": "Student Registration Form (Copy)",
  "description": "Fill this to register for Batch 4",
  "fields": [ ... ],
  "isActive": false,
  "token": "new-token-here",
  "startTime": "2026-04-01T00:00:00",
  "endTime": "2026-06-30T23:59:59",
  "isQuiz": false,
  "quizSettings": null,
  "limitOneResponse": true,
  "createdBy": "admin-uuid",
  "createdAt": "2026-04-17T11:00:00",
  "updatedAt": "2026-04-17T11:00:00"
}
```

---

## 8. Admin — Delete Form

`DELETE /api/forms/{id}`

By default performs a **soft delete** (moves to trash, recoverable). Pass `permanent=true` to hard-delete (also deletes all responses — irreversible).

**Required Auth:** Admin JWT

**Query Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `permanent` | No | `false` | `true` = permanently delete form + all its responses. `false` = soft delete (trash). |

```bash
# Soft delete (move to trash — recoverable)
curl -X DELETE http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"

# Permanent delete (IRREVERSIBLE — deletes form + all responses)
curl -X DELETE "http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890?permanent=true" \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
{ "message": "Form deleted" }
```
or (permanent):
```json
{ "message": "Form permanently deleted" }
```

**Error (404):** Form not found.

> **UI Recommendation:** Always show a confirmation dialog before permanent delete. This cannot be undone.

---

## 9. Admin — Restore Deleted Form

`POST /api/forms/{id}/restore`

Restores a soft-deleted form from the trash back to active forms.

**Required Auth:** Admin JWT

```bash
curl -X POST http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/restore \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
{ "message": "Form restored" }
```

**Error (404):** Form not found.

---

## 10. Admin — Responses & Analytics

### 10.1 GET — All Responses Across All Forms
`GET /api/forms/responses`

Returns every submission ever made across all forms. Used for the admin overview dashboard.

**Required Auth:** Admin JWT

```bash
curl http://localhost:8087/api/forms/responses \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "formId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "userEmail": "student@example.com",
    "score": null,
    "submissionData": {
      "field_1": "Rahul Singh",
      "field_2": "rahul@example.com",
      "field_3": "9876543210",
      "field_4": "Cybersecurity Fundamentals",
      "field_5": "Google"
    },
    "createdAt": "2026-04-17T10:30:00"
  },
  {
    "id": 2,
    "formId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "userEmail": "priya@example.com",
    "score": 85.0,
    "submissionData": {
      "q1": "Open Systems Interconnection",
      "q2": "Layer 3 - Network",
      "q3": "True"
    },
    "createdAt": "2026-04-17T11:00:00"
  }
]
```

> **`score`** is `null` for non-quiz forms and a number (0–100) for quiz forms.

---

### 10.2 GET — Responses for a Specific Form
`GET /api/forms/{formId}/responses`

Returns all responses submitted to one form.

**Required Auth:** Admin JWT

```bash
curl http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/responses \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200):** Array of submission objects (same shape as above).

**Empty list (no submissions yet):**
```json
[]
```

---

### 10.3 GET — Form Analytics
`GET /api/forms/{formId}/responses/analytics`

Returns aggregated analytics for a form — total responses, average score (quizzes), and per-question breakdowns.

**Required Auth:** Admin JWT

```bash
curl http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/responses/analytics \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin"
```

**Success Response (200) — Regular form:**
```json
{
  "formId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "totalResponses": 42,
  "averageScore": null,
  "questions": [
    {
      "label": "Which course are you interested in?",
      "fieldType": "dropdown",
      "optionCounts": {
        "Cybersecurity Fundamentals": 18,
        "Ethical Hacking": 12,
        "Network Security": 7,
        "AI & ML": 5
      },
      "recentAnswers": null
    },
    {
      "label": "Full Name",
      "fieldType": "text",
      "optionCounts": null,
      "recentAnswers": [
        "Rahul Singh",
        "Priya Sharma",
        "Arjun Mehta",
        "Sneha Reddy",
        "Vikram Nair"
      ]
    }
  ]
}
```

**Success Response (200) — Quiz form:**
```json
{
  "formId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "totalResponses": 25,
  "averageScore": 74.8,
  "questions": [
    {
      "label": "What does OSI stand for?",
      "fieldType": "radio",
      "optionCounts": {
        "Open Systems Interconnection": 20,
        "Open Software Interface": 3,
        "Operating System Integration": 2
      },
      "recentAnswers": null
    }
  ]
}
```

**Analytics behaviour by field type:**

| Field Type | `optionCounts` | `recentAnswers` |
|-----------|---------------|-----------------|
| `dropdown`, `radio`, `checkbox`, `rating`, `star_rating`, `declaration`, `declaration_terms` | Populated (option → count) | null |
| `text`, `textarea`, `email`, `number`, `date`, `date_dob`, `time`, `time_selection`, `file`, `file_upload` | null | Last 10 answers filled in |
| `section_header`, `paragraph`, `html` | null (skipped) | null (skipped) |

---

### 10.4 GET — Export Responses as CSV
`GET /api/forms/{formId}/responses/export`

Downloads all responses for a form as a CSV file. The browser will prompt to save.

**Required Auth:** Admin JWT

```bash
curl http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/responses/export \
  -H "Authorization: Bearer <admin_jwt>" \
  -H "X-User-Role: admin" \
  -o responses.csv
```

**Success Response (200):**
- `Content-Type: text/csv`
- `Content-Disposition: attachment; filename="responses.csv"`
- Body: CSV string with headers from field labels, rows = individual submissions

**CSV Format Example:**
```
Submission ID,Submitted At,Email,Full Name,Email Address,Phone Number,Which course are you interested in?
1,2026-04-17T10:30:00,rahul@example.com,Rahul Singh,rahul@example.com,9876543210,Cybersecurity Fundamentals
2,2026-04-17T11:00:00,priya@example.com,Priya Sharma,priya@example.com,9123456789,Ethical Hacking
```

> **Frontend tip:** To trigger a file download in JavaScript:
> ```javascript
> const blob = await response.blob();
> const url = window.URL.createObjectURL(blob);
> const a = document.createElement('a');
> a.href = url;
> a.download = 'responses.csv';
> a.click();
> ```

---

## 11. Public / Student — Get Form

`GET /api/forms/{id}`  
`GET /api/forms/{id}/public/{token}`  *(token-protected private forms)*

Retrieves a form for display. **No JWT needed.** Used by the student-facing form page.

---

### 11.1 — Get Public Form (Open Link)

```bash
curl http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Success Response (200):** Form object with fields.

**Error cases:**

| Scenario | HTTP | Response |
|----------|------|----------|
| Form not found | 404 | `{ "error": "Form not found" }` |
| Form is inactive (`isActive: false`) | 403 | `{ "error": "This form is not currently active" }` |
| Before `startTime` | 403 | `{ "error": "This form is not yet open" }` |
| After `endTime` | 403 | `{ "error": "This form has closed" }` |

---

### 11.2 — Get Token-Protected Form (Private Link)

When a form should only be accessible via a specific shared link (e.g., sent only to enrolled students):

```bash
curl "http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890?token=z9y8x7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4"
```

OR via the public path:

```bash
curl "http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/public/z9y8x7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4"
```

**Error (403):** Invalid token.

> **`token`** is returned when you create a form (in `FormResponseDTO.token`). Store it and use it to generate the private form URL for students.

---

## 12. Public / Student — Submit Response

`POST /api/forms/{formId}/responses`

Submits a filled form. **No JWT required.** Students can submit without an account.

---

### 12.1 Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userEmail` | string | Yes | Student's email (used to enforce one-response limit and send confirmation email) |
| `submissionData` | object | Yes | Key-value map: `{ "field_id": "answer_value" }` |

> **Key format:** Use the `id` value from each field definition in the `fields` array. If there's no `id`, use the `label` as the key. The backend looks up by `id` first, then falls back to `label`.

```bash
# Submit a regular form
curl -X POST http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/responses \
  -H "Content-Type: application/json" \
  -d '{
    "userEmail": "rahul@example.com",
    "submissionData": {
      "field_1": "Rahul Singh",
      "field_2": "rahul@example.com",
      "field_3": "9876543210",
      "field_4": "Cybersecurity Fundamentals",
      "field_5": "Google",
      "field_6": "I heard great things about this course",
      "field_7": "true"
    }
  }'
```

```bash
# Submit a quiz form
curl -X POST http://localhost:8087/api/forms/b2c3d4e5-f6a7-8901-bcde-f12345678901/responses \
  -H "Content-Type: application/json" \
  -d '{
    "userEmail": "priya@example.com",
    "submissionData": {
      "q1": "Open Systems Interconnection",
      "q2": "Layer 3 - Network",
      "q3": "True",
      "q4": "3"
    }
  }'
```

---

### 12.2 Checkbox Submission

For checkbox fields (multi-select), send the selected values as a **comma-separated string** or an **array**:

```json
{
  "submissionData": {
    "field_skills": "Python,JavaScript,SQL",
    "field_interests": ["Cybersecurity", "AI", "Cloud"]
  }
}
```

---

### 12.3 File Upload Fields

For file fields, first upload the file to the course-service or a storage endpoint, then submit the returned URL as the field value:

```json
{
  "submissionData": {
    "field_resume": "https://res.cloudinary.com/dt6rxrpqr/raw/upload/v1/resume.pdf"
  }
}
```

---

### 12.4 Success Responses

**Regular form — Success (201):**
```json
{
  "id": 43,
  "formId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userEmail": "rahul@example.com",
  "score": null,
  "submissionData": {
    "field_1": "Rahul Singh",
    "field_2": "rahul@example.com",
    "field_3": "9876543210",
    "field_4": "Cybersecurity Fundamentals",
    "field_5": "Google"
  },
  "createdAt": "2026-04-17T10:30:00"
}
```

**Quiz form — Success (201):** Score is automatically calculated:
```json
{
  "id": 26,
  "formId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "userEmail": "priya@example.com",
  "score": 80.0,
  "submissionData": {
    "q1": "Open Systems Interconnection",
    "q2": "Layer 3 - Network",
    "q3": "True"
  },
  "createdAt": "2026-04-17T11:00:00"
}
```

> **`score`** = percentage (0–100). Calculated by: summing `points` of each correctly answered question / total possible points × 100.

---

### 12.5 Error Responses

| Scenario | HTTP | Response |
|----------|------|----------|
| Form not found | 404 | `{ "error": "Form not found" }` |
| Form is inactive | 403 | `{ "error": "This form is not currently active" }` |
| Form not yet open (`startTime` in future) | 403 | `{ "error": "This form is not yet open" }` |
| Form is closed (`endTime` passed) | 403 | `{ "error": "This form has closed" }` |
| One-response limit triggered | 409 | `{ "error": "You have already submitted this form" }` |
| Required field missing | 400 | `{ "error": "Validation failed", "message": "Field 'Full Name' is required" }` |
| Email field has invalid format | 400 | `{ "error": "Validation failed", "message": "Field 'Email Address' must be a valid email" }` |
| Dropdown/radio — value not in options | 400 | `{ "error": "Validation failed", "message": "Field 'Course' has an invalid option" }` |
| Checkbox — one or more values not in options | 400 | `{ "error": "Validation failed", "message": "Field 'Skills' has an invalid option" }` |
| Time field invalid format | 400 | `{ "error": "Validation failed", "message": "Field 'Time' must be in HH:MM format" }` |
| Date field invalid format | 400 | `{ "error": "Validation failed", "message": "Field 'DOB' must be in YYYY-MM-DD format" }` |
| Rating out of range | 400 | `{ "error": "Validation failed", "message": "Field 'Rating' must be between 0 and 5" }` |
| Declaration not accepted | 400 | `{ "error": "Validation failed", "message": "You must accept the declaration" }` |

> After a successful submission, the service automatically sends a **confirmation email** to `userEmail` via the notification-service (non-blocking — form submission still succeeds even if email fails).

---

## 13. Public — Check if Already Responded

`GET /api/forms/{formId}/responses/check`

Check if an email has already submitted this form. Use this **before** showing the form to a returning user to avoid showing a form they already filled.

**No JWT required.**

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `email` | Yes | The email address to check |

```bash
curl "http://localhost:8087/api/forms/a1b2c3d4-e5f6-7890-abcd-ef1234567890/responses/check?email=rahul@example.com"
```

**Success Response (200):**
```json
{ "alreadyResponded": true }
```
or
```json
{ "alreadyResponded": false }
```

**Frontend usage:**
```javascript
const res = await fetch(`/api/forms/${formId}/responses/check?email=${encodeURIComponent(email)}`);
const { alreadyResponded } = await res.json();

if (alreadyResponded) {
  showMessage("You have already submitted this form.");
} else {
  showForm();
}
```

---

## 14. Field Types Reference

Each field in the `fields` array is an object. Common properties:

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | string | Yes | Unique key used in `submissionData` |
| `label` | string | Yes | Question/field display text |
| `field_type` | string | Yes | One of the types below |
| `is_required` | boolean | No | Whether the field must be answered |
| `placeholder` | string | No | Input placeholder text |
| `options` | array | Depends | Required for choice-type fields |
| `points` | integer | No | Points value for quiz scoring |
| `correct_answer` | string | No | Correct answer for quiz auto-grading |

---

### Input Types

| Type | UI Component | Submission Value | Notes |
|------|-------------|-----------------|-------|
| `text` | Single-line text input | Any string | |
| `textarea` | Multi-line text area | Any string | |
| `email` | Email input | Valid email string | Auto-validated |
| `number` | Number input | Numeric string | Auto-validated as parseable number |
| `date` | Date picker | `"YYYY-MM-DD"` | Validated strictly |
| `date_dob` | Date picker (Date of Birth) | `"YYYY-MM-DD"` | Same as date |
| `time` | Time picker | `"HH:MM"` | 24-hour format |
| `time_selection` | Time selector | `"HH:MM"` | Same as time |
| `file` | File input | URL string | Upload file first, submit the URL |
| `file_upload` | File upload widget | URL string | Same as file |

### Choice Types

| Type | UI Component | Submission Value | `options` Required |
|------|-------------|-----------------|-------------------|
| `radio` | Radio buttons | Single option string | Yes |
| `dropdown` | Select/dropdown | Single option string | Yes |
| `checkbox` | Checkboxes (multi-select) | Comma-separated or array | Yes |

### Rating Types

| Type | UI Component | Submission Value | `options` |
|------|-------------|-----------------|----------|
| `rating` | Numeric rating | Integer string (e.g., `"4"`) | `options[0]` = max stars (default `5`) |
| `star_rating` | Star rating | Integer string (e.g., `"4"`) | Same as rating |

### Agreement Types

| Type | UI Component | Submission Value | Notes |
|------|-------------|-----------------|-------|
| `declaration` | Checkbox/statement | `"true"` or `"on"` | If required, must be `"true"` |
| `declaration_terms` | Terms & conditions checkbox | `"true"` or `"on"` | Same as declaration |

### Layout/Display Types *(No submission data needed)*

| Type | UI Component | Notes |
|------|-------------|-------|
| `section_header` | Section divider with title | Skipped in validation and analytics |
| `paragraph` | Static text block/description | Skipped in validation and analytics |
| `html` | Raw HTML content block | Skipped in validation and analytics |

---

### Complete Field Definition Examples

```json
// Text field
{ "id": "name", "label": "Full Name", "field_type": "text", "is_required": true, "placeholder": "Enter your full name" }

// Email field
{ "id": "email", "label": "Email Address", "field_type": "email", "is_required": true }

// Dropdown
{ "id": "course", "label": "Select Course", "field_type": "dropdown", "is_required": true, "options": ["Cybersecurity", "Networking", "AI"] }

// Radio (single choice)
{ "id": "experience", "label": "Your Experience Level", "field_type": "radio", "is_required": true, "options": ["Beginner", "Intermediate", "Advanced"] }

// Checkbox (multi-select)
{ "id": "skills", "label": "Skills You Have", "field_type": "checkbox", "is_required": false, "options": ["Python", "Linux", "Networking", "JavaScript"] }

// Star rating (max 5)
{ "id": "rating", "label": "Rate this course", "field_type": "star_rating", "is_required": false, "options": ["5"] }

// Date of Birth
{ "id": "dob", "label": "Date of Birth", "field_type": "date_dob", "is_required": true }

// File upload
{ "id": "resume", "label": "Upload Resume", "field_type": "file_upload", "is_required": false }

// Declaration / Terms
{ "id": "terms", "label": "I agree to the terms and conditions", "field_type": "declaration_terms", "is_required": true }

// Section header (no data collected)
{ "id": "sec_1", "label": "Personal Information", "field_type": "section_header" }

// Paragraph text (no data collected)
{ "id": "para_1", "label": "Please fill in all fields accurately.", "field_type": "paragraph" }

// Quiz question (radio with correct answer)
{ "id": "q1", "label": "What is TCP/IP?", "field_type": "radio", "is_required": true, "points": 2, "options": ["A protocol suite", "An OS", "A database"], "correct_answer": "A protocol suite" }
```

---

## 15. Error Reference

| HTTP Status | When It Happens | What to Show User |
|------------|----------------|------------------|
| `201` | Response submitted successfully | Show thank-you/confirmation message |
| `200` | GET request succeeded | Render data |
| `400` | Validation failed (missing required field, wrong format, invalid option) | Show the `message` from response body next to the offending field |
| `403` | Form inactive, not yet open, or closed. OR not admin for protected endpoints. | Show the `error` message. For admin 403 → redirect to login |
| `404` | Form or response not found | Show "Form not found" |
| `409` | Student already submitted (one-response limit) | "You have already submitted this form" |
| `401` | Missing or invalid JWT on admin endpoints | Redirect to login |
| `500` | Unexpected server error | "Something went wrong, please try again" |

**Standard error body:**
```json
{
  "error": "Short error label",
  "message": "Human-readable description"
}
```

---

## 16. Quick Reference — All Endpoints

### Admin Panel Endpoints (JWT Required — Admin Only)

| # | Method | Path | Description |
|---|--------|------|-------------|
| 1 | GET | `/api/forms?view=active` | List all active forms |
| 2 | GET | `/api/forms?view=trash` | List all trashed/deleted forms |
| 3 | GET | `/api/forms/{id}` | View/edit a single form (admin view) |
| 4 | POST | `/api/forms` | Create a new form |
| 5 | PUT | `/api/forms/{id}` | Update/edit a form |
| 6 | PATCH | `/api/forms/{id}/active` | Toggle form active/inactive |
| 7 | POST | `/api/forms/{id}/duplicate` | Duplicate a form |
| 8 | DELETE | `/api/forms/{id}` | Soft delete (to trash) |
| 9 | DELETE | `/api/forms/{id}?permanent=true` | Permanent delete (IRREVERSIBLE) |
| 10 | POST | `/api/forms/{id}/restore` | Restore from trash |
| 11 | GET | `/api/forms/responses` | All responses across all forms |
| 12 | GET | `/api/forms/{formId}/responses` | All responses for one form |
| 13 | GET | `/api/forms/{formId}/responses/analytics` | Analytics/aggregation for a form |
| 14 | GET | `/api/forms/{formId}/responses/export` | Download responses as CSV |

### Student / Teacher / Public Endpoints (No JWT Required)

| # | Method | Path | Description |
|---|--------|------|-------------|
| 15 | GET | `/api/forms/{id}` | View a form (public/open) |
| 16 | GET | `/api/forms/{id}?token={token}` | View a token-protected form |
| 17 | GET | `/api/forms/{id}/public/{token}` | View a form via token URL (alternative) |
| 18 | POST | `/api/forms/{formId}/responses` | Submit a response to a form |
| 19 | GET | `/api/forms/{formId}/responses/check?email={email}` | Check if email already submitted |

---

## Typical Frontend Flows

### Admin Panel — Form Management Flow

```
1. GET /api/forms?view=active              → list all forms on the Forms page
2. POST /api/forms                         → "Create Form" button → new form wizard
3. GET /api/forms/{id}                     → open form editor/detail view
4. PUT /api/forms/{id}                     → "Save Changes" in editor
5. PATCH /api/forms/{id}/active            → toggle switch to enable/disable
6. POST /api/forms/{id}/duplicate          → "Duplicate" button
7. DELETE /api/forms/{id}                  → "Delete" → moves to trash
8. GET /api/forms?view=trash               → Trash view
9. POST /api/forms/{id}/restore            → "Restore" from trash
10. DELETE /api/forms/{id}?permanent=true  → "Empty Trash" / "Delete Forever"
11. GET /api/forms/{formId}/responses      → "View Responses" tab
12. GET /api/forms/{formId}/responses/analytics → "Analytics" tab
13. GET /api/forms/{formId}/responses/export    → "Export CSV" button
```

### Student — Form Submission Flow

```
1. GET /api/forms/{id}/responses/check?email={email}
   → if alreadyResponded=true: show "Already submitted" message, stop
   → if alreadyResponded=false: continue

2. GET /api/forms/{id}
   → if 403 or form isActive=false: show appropriate message
   → if 200: render the form fields

3. Student fills in the form

4. POST /api/forms/{id}/responses
   → if 201: show thank-you page
   → if 400: highlight validation error on the specific field
   → if 409: show "You have already filled this form"
   → if 403: show "This form is closed/not yet open"
```

### Generate a Private Form Link for Students
```javascript
// After creating a form, you get back `token`
const formUrl = `https://yourdomain.com/forms/${form.id}?token=${form.token}`;
// or
const formUrl = `https://yourdomain.com/forms/${form.id}/public/${form.token}`;

// Share this URL via email/notification to specific students
```
