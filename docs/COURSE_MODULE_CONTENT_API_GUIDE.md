# Course, Module & Content API Guide

**Base URL:** `http://localhost:8080` (via Gateway)  
**Auth:** All endpoints require a valid JWT in the `Authorization: Bearer <token>` header.  
The gateway automatically injects `X-User-Id` and `X-User-Role` from the token — the frontend does NOT send these manually.

**Role Values in JWT:** `admin`, `teacher`, `student`, `dual`  
> The UI shows "Administrator" but the JWT stores `admin` (lowercase). The backend handles both.

---

## 1. Course Endpoints (`/api/courses` and `/api/course-management`)

### 1.1 List All Courses
```
GET /api/courses
Authorization: Bearer <token>
```

**Response:**
```json
{
  "success": true,
  "courses": [
    {
      "id": 1,
      "title": "Cybersecurity Fundamentals",
      "description": "...",
      "category": "cybersecurity",
      "difficultyLevel": "BEGINNER",
      "duration": "10h",
      "thumbnailUrl": "https://res.cloudinary.com/...",
      "basePrice": 100.0,
      "gstPercent": 18,
      "finalPrice": 118.0,
      "isActive": true,
      "status": "APPROVED",
      "createdBy": "uuid-of-creator",
      "createdAt": "2026-04-17T19:55:23",
      "updatedAt": "2026-04-18T10:00:00",
      "moduleCount": 3
    }
  ]
}
```

> **`moduleCount`** — use this to display "N modules" on the course card. No extra call needed.

**Visibility rules by role:**
| Role | Sees |
|---|---|
| `admin` | All courses |
| `teacher` | Own courses + assigned courses |
| `student` | Active/approved courses only |

---

### 1.2 Create a Course
```
POST /api/course-management/courses
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Cybersecurity Fundamentals",
  "description": "Learn the basics of cybersecurity",
  "category": "cybersecurity",
  "difficultyLevel": "BEGINNER",
  "duration": "10h",
  "thumbnailUrl": "https://res.cloudinary.com/...",
  "contentUrl": "",
  "basePrice": 100.0,
  "gstPercent": 18,
  "isActive": true
}
```

> `finalPrice` is auto-calculated: `basePrice + (basePrice × gstPercent / 100)`.  
> `gstPercent` defaults to 18% if not provided.

**Thumbnail Upload (2-step):**
1. `POST /api/materials/upload/thumbnail` with `multipart/form-data { file: <image> }` → returns `{ "success": true, "url": "https://res.cloudinary.com/..." }`
2. Use the returned `url` as `thumbnailUrl` in the course creation body.

**Response:**
```json
{
  "success": true,
  "course": { "id": 1, "title": "...", ... }
}
```

---

### 1.3 Update a Course
```
PUT /api/course-management/courses/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body** (all fields optional — only send what changed):
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "thumbnailUrl": "https://res.cloudinary.com/...",
  "basePrice": 150.0,
  "isActive": false
}
```

**Response:**
```json
{ "success": true, "course": { ... } }
```

---

### 1.4 Delete a Course
```
DELETE /api/course-management/courses/{id}
Authorization: Bearer <token>
```
> Admin only.

**Response:**
```json
{ "success": true, "message": "Course deleted successfully" }
```

---

## 2. Module Endpoints

### 2.1 Get All Modules for a Course (with Contents)
```
GET /api/course-management/courses/{courseId}/full
Authorization: Bearer <token>
```
> Requires role: `admin` or `teacher`

**Response:**
```json
{
  "success": true,
  "course": {
    "id": 1,
    "title": "Cybersecurity Fundamentals",
    ...
  },
  "modules": [
    {
      "id": 1,
      "title": "Introduction",
      "description": "Module overview",
      "orderIndex": 1,
      "isActive": true,
      "createdAt": "2026-04-18T10:00:00",
      "contents": [
        {
          "id": 10,
          "title": "Lecture 1 - What is Cybersecurity?",
          "contentType": "LECTURE",
          "orderIndex": 1,
          "durationMinutes": 30,
          "videoUrl": "https://res.cloudinary.com/...",
          "isPreview": true
        }
      ]
    }
  ]
}
```

> **`contents` is nested inside each module** — no extra call needed to populate the module contents list.

---

### 2.2 Add a Module to a Course
```
POST /api/course-management/courses/{courseId}/modules
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "title": "Module 1 - Introduction",
  "description": "Overview of the course topics",
  "orderIndex": 1
}
```
> `orderIndex` is optional — auto-assigned as next in sequence if omitted.

**Response:**
```json
{
  "success": true,
  "module": {
    "id": 1,
    "title": "Module 1 - Introduction",
    "orderIndex": 1,
    ...
  }
}
```

> **After success → re-fetch** `GET /api/course-management/courses/{courseId}/full` to refresh the module list in the UI.

---

### 2.3 Update a Module
```
PUT /api/course-management/modules/{moduleId}
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body** (all optional):
```json
{
  "title": "Updated Module Title",
  "description": "Updated description",
  "orderIndex": 2
}
```

**Response:**
```json
{ "success": true, "module": { ... } }
```

---

### 2.4 Delete a Module
```
DELETE /api/course-management/modules/{moduleId}
Authorization: Bearer <token>
```
> Admin only.

**Response:**
```json
{ "success": true, "message": "Module deleted successfully" }
```

---

## 3. Content Endpoints

### 3.1 Get Contents of a Module
```
GET /api/course-management/modules/{moduleId}/contents
Authorization: Bearer <token>
```
> Requires role: `admin` or `teacher`

**Response:**
```json
{
  "success": true,
  "moduleTitle": "Introduction",
  "contents": [
    {
      "id": 10,
      "title": "Lecture 1",
      "contentType": "LECTURE",
      "orderIndex": 1,
      "durationMinutes": 30,
      "videoUrl": "https://...",
      "isPreview": true,
      "attachmentUrl": null
    },
    {
      "id": 11,
      "title": "Quiz 1",
      "contentType": "QUIZ",
      "orderIndex": 2,
      "timeLimitMinutes": 20,
      "passingScore": 70,
      "maxAttempts": 3
    }
  ]
}
```

---

### 3.2 Add Content to a Module
```
POST /api/course-management/modules/{moduleId}/contents
Authorization: Bearer <token>
Content-Type: application/json
```

**Content Types and their fields:**

#### LECTURE / VIDEO
```json
{
  "title": "Lesson 1 - Introduction",
  "contentType": "LECTURE",
  "description": "Overview video",
  "videoUrl": "https://res.cloudinary.com/...",
  "contentText": "Optional text transcript",
  "durationMinutes": 30,
  "isPreview": true,
  "attachmentUrl": "https://...",
  "orderIndex": 1
}
```

#### QUIZ / EXAM
```json
{
  "title": "Module Quiz",
  "contentType": "QUIZ",
  "description": "Test your knowledge",
  "timeLimitMinutes": 20,
  "passingScore": 70,
  "maxAttempts": 3,
  "orderIndex": 2
}
```

#### LAB
```json
{
  "title": "Hands-on Lab",
  "contentType": "LAB",
  "description": "Practice exercise",
  "labType": "VIRTUAL",
  "instructions": "Follow the steps...",
  "environmentConfig": "{}",
  "durationMinutes": 45,
  "orderIndex": 3
}
```

#### ASSIGNMENT
```json
{
  "title": "Final Assignment",
  "contentType": "ASSIGNMENT",
  "description": "Submit your project",
  "assignmentType": "FILE_UPLOAD",
  "instructions": "Upload your project ZIP",
  "maxScore": 100,
  "orderIndex": 4
}
```

**Response:**
```json
{
  "success": true,
  "content": { "id": 10, "title": "...", "contentType": "LECTURE", ... }
}
```

> **After success → re-fetch** `GET /api/course-management/courses/{courseId}/full` to refresh the UI.

---

### 3.3 Update Content
```
PUT /api/course-management/contents/{contentId}
Authorization: Bearer <token>
Content-Type: application/json
```

Send only the fields you want to update (same fields as creation above).

**Response:**
```json
{ "success": true, "content": { ... } }
```

---

### 3.4 Delete Content
```
DELETE /api/course-management/contents/{contentId}
Authorization: Bearer <token>
```
> Admin only.

**Response:**
```json
{ "success": true, "message": "Content deleted successfully" }
```

---

### 3.5 Add Questions to a Quiz
```
POST /api/course-management/contents/{quizContentId}/quiz/questions
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body:**
```json
[
  {
    "questionText": "What does CIA stand for in cybersecurity?",
    "questionType": "MULTIPLE_CHOICE",
    "points": 10,
    "explanation": "CIA = Confidentiality, Integrity, Availability",
    "options": [
      { "optionText": "Confidentiality, Integrity, Availability", "isCorrect": true },
      { "optionText": "Control, Integrity, Access", "isCorrect": false },
      { "optionText": "Cyber, Intelligence, Assurance", "isCorrect": false }
    ]
  }
]
```

**Response:**
```json
{ "success": true, "count": 1 }
```

---

## 4. File Upload

### Upload Thumbnail (for courses)
```
POST /api/materials/upload/thumbnail
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <image file>
```
> Max size: 5MB. Allowed: jpg, png, gif, webp, svg.

**Response:**
```json
{ "success": true, "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/..." }
```

### Upload Video (for lecture content)
```
POST /api/materials/upload/video
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <video file>
```
> Max size: 500MB. Role: `teacher` or `admin` only.

**Response:**
```json
{ "success": true, "url": "https://res.cloudinary.com/dt6rxrpqr/video/upload/..." }
```

### Upload Document (for attachments)
```
POST /api/materials/upload/document
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <pdf/doc file>
```
> Max size: 50MB. Role: `teacher` or `admin` only.

**Response:**
```json
{ "success": true, "url": "https://res.cloudinary.com/dt6rxrpqr/raw/upload/..." }
```

---

## 5. Typical UI Flows

### Admin — Course Management Page
```
1. Page load     → GET /api/courses
2. Click course  → GET /api/course-management/courses/{id}/full
3. Add module    → POST /api/course-management/courses/{id}/modules
                   → re-fetch GET /api/course-management/courses/{id}/full
4. Add content   → POST /api/course-management/modules/{moduleId}/contents
                   → re-fetch GET /api/course-management/courses/{id}/full
5. Edit course   → PUT /api/course-management/courses/{id}
                   → re-fetch GET /api/courses
6. Delete course → DELETE /api/course-management/courses/{id}
                   → re-fetch GET /api/courses
```

### Create Course with Thumbnail
```
1. User picks image file
2. POST /api/materials/upload/thumbnail  → get url
3. POST /api/course-management/courses with thumbnailUrl = url
```

---

## 6. Error Responses

| HTTP Status | Meaning |
|---|---|
| `200 OK` | Success |
| `201 Created` | Resource created |
| `400 Bad Request` | Invalid input (e.g. bad contentType) |
| `403 Forbidden` | Role not allowed or not assigned to course |
| `404 Not Found` | Course / module / content ID doesn't exist |
| `500 Internal Server Error` | Server-side failure |

All errors return:
```json
{ "error": "Human-readable error message" }
```
