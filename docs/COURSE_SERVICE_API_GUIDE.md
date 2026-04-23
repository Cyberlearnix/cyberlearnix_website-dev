# Course Service — Frontend API Guide

**Service:** course-service  
**Base URL:** `http://localhost:8082`  
**Database:** PostgreSQL — `cyberlearnix_courses` (port 5999)  
**Total Endpoints:** 60+  

---

## Table of Contents

1. [Service Overview](#1-service-overview)
2. [Courses](#2-courses)
3. [Course Management (Admin/Teacher)](#3-course-management-adminteacher)
4. [Course Teachers](#4-course-teachers)
5. [Modules & Contents](#5-modules--contents)
6. [Quiz Questions](#6-quiz-questions)
7. [Progress Tracking](#7-progress-tracking)
8. [Material Uploads](#8-material-uploads)
9. [Banners](#9-banners)
10. [Promo Banners](#10-promo-banners)
11. [Certificates](#11-certificates)
12. [Partners](#12-content-partners)
13. [Suggestions](#13-course-suggestions)
14. [Content Updates (News Feed)](#14-content-updates-news-feed)
15. [Content Reviews](#15-content-reviews)
16. [Teacher Dashboard](#16-teacher-dashboard)
17. [Admin Stats](#17-admin-stats-courses)
18. [WebSocket Events](#18-websocket-real-time-events)
19. [Data Models Reference](#19-data-models-reference)
20. [Error Reference](#20-error-reference)

---

## 1. Service Overview

The course service manages the entire learning content hierarchy:

```
Course
 └── CourseModule (ordered sections)
       └── ModuleContent (typed items)
             ├── LectureContent  (video/text lessons)
             ├── LabContent      (hands-on labs)
             ├── AssignmentContent (projects/case studies)
             └── QuizContent     (quizzes/exams with questions)
```

**Role-based visibility:**
- `student` — sees only active/published courses they are enrolled in
- `teacher` — sees courses they created or are assigned to
- `admin` — sees everything

**Common Headers (role-protected endpoints):**

| Header | Value | Notes |
|--------|-------|-------|
| `X-User-Id` | user UUID string | Logged-in user's ID |
| `X-User-Role` | `student`, `teacher`, `admin`, `dual` | User's role |
| `Content-Type` | `application/json` | For POST/PUT/PATCH requests |

> In production the gateway injects these from the JWT token automatically.

---

## 2. Courses

**Base path:** `/api/courses`

### 2.1 GET — List Courses / Get Single Course
`GET /api/courses`

Returns courses based on the caller's role.

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `id` | No | If provided, returns a single course object instead of a list |

**Role-based behaviour:**

| Role | What is returned |
|------|-----------------|
| `admin` | All courses in the system |
| `teacher` / `dual` | Courses the teacher created + courses assigned to them |
| `student` | Active courses only (suitable for the public course listing page) |
| No header | Active courses only (public/guest view) |

```bash
# Public / guest — active courses list
curl http://localhost:8082/api/courses

# Admin — all courses (active + inactive)
curl http://localhost:8082/api/courses \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -H "X-User-Role: admin"

# Teacher — own + assigned courses
curl http://localhost:8082/api/courses \
  -H "X-User-Id: teacher-uuid-here" \
  -H "X-User-Role: teacher"

# Get a single course by ID
curl "http://localhost:8082/api/courses?id=10" \
  -H "X-User-Id: student-uuid" \
  -H "X-User-Role: student"
```

**Success Response (200) — list:**
```json
[
  {
    "id": 10,
    "title": "Cybersecurity Fundamentals",
    "description": "Learn the basics of cybersecurity from scratch.",
    "category": "Cybersecurity",
    "difficultyLevel": "BEGINNER",
    "duration": "12h 30m",
    "contentUrl": null,
    "thumbnailUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/v1/thumb.jpg",
    "basePrice": 4999.0,
    "gstPercent": 18,
    "finalPrice": 5898.82,
    "isActive": true,
    "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
    "status": "APPROVED",
    "createdAt": "2026-01-10T10:00:00",
    "updatedAt": "2026-04-01T09:00:00"
  },
  ...
]
```

**Success Response (200) — single course (when `?id=` is passed):**
```json
{
  "id": 10,
  "title": "Cybersecurity Fundamentals",
  "description": "...",
  "category": "Cybersecurity",
  "difficultyLevel": "BEGINNER",
  "duration": "12h 30m",
  "basePrice": 4999.0,
  "gstPercent": 18,
  "finalPrice": 5898.82,
  "isActive": true,
  "status": "APPROVED",
  "createdBy": "9f4ea44b...",
  "thumbnailUrl": "https://...",
  "createdAt": "2026-01-10T10:00:00",
  "updatedAt": "2026-04-01T09:00:00"
}
```

---

### 2.2 GET — Course Curriculum (Public)
`GET /api/courses/{id}/curriculum`

Returns the course title and an ordered list of module titles — used for the public course detail/landing page preview. **No auth headers needed.**

```bash
curl http://localhost:8082/api/courses/10/curriculum
```

**Success Response (200):**
```json
{
  "courseId": 10,
  "title": "Cybersecurity Fundamentals",
  "modules": [
    { "id": 1, "title": "Introduction to Cybersecurity", "orderIndex": 1 },
    { "id": 2, "title": "Network Security Basics", "orderIndex": 2 },
    { "id": 3, "title": "Threats and Vulnerabilities", "orderIndex": 3 }
  ]
}
```

**Error Response (404):** Course not found.

---

### 2.3 POST — Create Course
`POST /api/courses`

Creates a new course. The creator is automatically assigned as a teacher of the course.

**Required Headers:** `X-User-Id`, `X-User-Role`

**Permission rule:** Non-admin teachers must have `canCreateCourses = true` permission (set by admin).

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Course title |
| `description` | string | No | Full description |
| `category` | string | No | e.g. `Cybersecurity`, `AI`, `Network` |
| `difficultyLevel` | string | No | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `duration` | string | No | e.g. `"12h 30m"` |
| `thumbnailUrl` | string | No | Cloudinary URL (upload first via `/api/materials/upload/thumbnail`) |
| `contentUrl` | string | No | External content URL |
| `basePrice` | number | No | Base price in INR (GST is calculated automatically) |
| `gstPercent` | integer | No | Defaults to 18 |
| `isActive` | boolean | No | Defaults to `true` |

```bash
curl -X POST http://localhost:8082/api/courses \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Ethical Hacking Masterclass",
    "description": "Complete hands-on ethical hacking course covering all CEH topics.",
    "category": "Cybersecurity",
    "difficultyLevel": "ADVANCED",
    "duration": "40h",
    "basePrice": 9999.0,
    "gstPercent": 18,
    "isActive": true,
    "thumbnailUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/v1/thumb.jpg"
  }'
```

**Success Response (201):**
```json
{
  "id": 15,
  "title": "Ethical Hacking Masterclass",
  "description": "Complete hands-on ethical hacking course...",
  "category": "Cybersecurity",
  "difficultyLevel": "ADVANCED",
  "duration": "40h",
  "basePrice": 9999.0,
  "gstPercent": 18,
  "finalPrice": 11798.82,
  "isActive": true,
  "status": "APPROVED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "thumbnailUrl": "https://res.cloudinary.com/...",
  "createdAt": "2026-04-17T10:00:00",
  "updatedAt": "2026-04-17T10:00:00"
}
```

**Error (403) — teacher without permission:**
```json
{ "error": "You do not have permission to create courses" }
```

---

### 2.4 PUT — Update Course
`PUT /api/courses/{id}`

Updates a course. Admin can update any; teacher can only update their own assigned courses.

**Required Headers:** `X-User-Id`, `X-User-Role`

**Request Body:** Same fields as POST (send only the fields you want to change).

```bash
curl -X PUT http://localhost:8082/api/courses/15 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Ethical Hacking Masterclass v2",
    "isActive": false
  }'
```

**Success Response (200):** Updated course object.  
**Error (403):** Not the course creator/assigned teacher.  
**Error (404):** Course not found.

---

### 2.5 DELETE — Delete Course
`DELETE /api/courses/{id}`

**Required Headers:** `X-User-Id`, `X-User-Role`

```bash
curl -X DELETE http://localhost:8082/api/courses/15 \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
{ "message": "Course deleted successfully" }
```

**Error (403):** Not admin or assigned teacher.  
**Error (404):** Course not found.

---

## 3. Course Management (Admin/Teacher)

**Base path:** `/api/course-management`

This is the **primary management API** used by the admin panel and teacher dashboard. It includes full course CRUD + module/content CRUD in one place.

---

### 3.1 POST — Create Course
`POST /api/course-management/courses`

Same as `/api/courses` POST. Checks `canCreateCourses` permission.

```bash
curl -X POST http://localhost:8082/api/course-management/courses \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Network Security Basics",
    "description": "Core network security concepts",
    "category": "Network",
    "difficultyLevel": "INTERMEDIATE",
    "duration": "20h",
    "basePrice": 7999.0
  }'
```

**Success Response (201):** Course object.  
**Error (403):** `{ "error": "You do not have permission to create courses" }`

---

### 3.2 PUT — Update Course
`PUT /api/course-management/courses/{id}`

Checks `canEditCourses` permission for non-admin teachers.

```bash
curl -X PUT http://localhost:8082/api/course-management/courses/10 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Network Security Basics (Updated)",
    "duration": "22h"
  }'
```

**Success Response (200):** Updated course object.

---

### 3.3 DELETE — Delete Course (Admin Only)
`DELETE /api/course-management/courses/{id}`

```bash
curl -X DELETE http://localhost:8082/api/course-management/courses/10 \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin"
```

**Success Response (200):** `{ "message": "Course deleted" }`  
**Error (403):** Not admin.

---

### 3.4 GET — Get Full Course with All Modules
`GET /api/course-management/courses/{id}/full`

Returns a course with ALL its modules and all content items per module. Used by the course editor.

**Required Headers:** `X-User-Id`, `X-User-Role` (admin or assigned teacher only)

```bash
curl http://localhost:8082/api/course-management/courses/10/full \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher"
```

**Success Response (200):**
```json
{
  "id": 10,
  "title": "Cybersecurity Fundamentals",
  "description": "...",
  "category": "Cybersecurity",
  "difficultyLevel": "BEGINNER",
  "isActive": true,
  "modules": [
    {
      "id": 1,
      "title": "Introduction to Cybersecurity",
      "description": "Overview of security concepts",
      "orderIndex": 1,
      "isActive": true,
      "contents": [
        {
          "id": 101,
          "title": "What is Cybersecurity?",
          "contentType": "LECTURE",
          "orderIndex": 1,
          "isActive": true,
          "videoUrl": "https://res.cloudinary.com/.../video.mp4",
          "durationMinutes": 15,
          "isPreview": true
        },
        {
          "id": 102,
          "title": "Week 1 Lab",
          "contentType": "LAB",
          "orderIndex": 2,
          "labType": "HANDS_ON",
          "durationMinutes": 60
        }
      ]
    }
  ]
}
```

**Error (403):** Course not accessible to this teacher.

---

### 3.5 GET — Course Enrolled Students with Progress
`GET /api/course-management/courses/{id}/students`

Returns all students enrolled in a course, with their progress percentages.

**Required Headers:** `X-User-Id`, `X-User-Role` (admin or assigned teacher)

```bash
curl http://localhost:8082/api/course-management/courses/10/students \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher"
```

**Success Response (200):**
```json
[
  {
    "studentId": "student-uuid-1",
    "studentName": "Rahul Singh",
    "email": "rahul@example.com",
    "progress": 65,
    "enrolledAt": "2026-03-01T10:00:00",
    "completedAt": null
  },
  {
    "studentId": "student-uuid-2",
    "studentName": "Priya Sharma",
    "email": "priya@example.com",
    "progress": 100,
    "enrolledAt": "2026-02-15T08:00:00",
    "completedAt": "2026-04-10T16:00:00"
  }
]
```

---

### 3.6 GET — Teacher Permissions
`GET /api/course-management/teacher-permissions/{teacherId}`

Admin-only. Returns what a teacher is and isn't allowed to do.

```bash
curl http://localhost:8082/api/course-management/teacher-permissions/teacher-uuid \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
{
  "canCreateCourses": true,
  "canEditCourses": true,
  "canDeleteCourses": false,
  "canAddModules": true,
  "canEditModules": true,
  "canDeleteModules": false,
  "canAddContent": true,
  "canEditContent": true,
  "canManageExams": false,
  "maxModulesPerCourse": 10,
  "maxContentPerModule": 20
}
```

---

### 3.7 PUT — Update Teacher Permissions (Admin Only)
`PUT /api/course-management/teacher-permissions/{teacherId}`

```bash
curl -X PUT http://localhost:8082/api/course-management/teacher-permissions/teacher-uuid \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin" \
  -d '{
    "canCreateCourses": true,
    "canEditCourses": true,
    "canDeleteCourses": false,
    "canAddModules": true,
    "canEditModules": true,
    "canDeleteModules": false,
    "canAddContent": true,
    "canEditContent": true,
    "canManageExams": true,
    "maxModulesPerCourse": 15,
    "maxContentPerModule": 30
  }'
```

**Success Response (200):** Updated permissions object.  
**Error (403):** Not admin.

---

## 4. Course Teachers

**Base path:** `/api/courses/teachers`

Manages which teachers are assigned to which courses.

---

### 4.1 GET — Check if Teacher is Assigned
`GET /api/courses/teachers/exists`

```bash
curl "http://localhost:8082/api/courses/teachers/exists?teacherId=teacher-uuid&courseId=10"
```

**Success Response (200):**
```json
true
```
or
```json
false
```

---

### 4.2 GET — List Assignments
`GET /api/courses/teachers`

**Query Parameters (use one or the other):**

| Parameter | Description |
|-----------|-------------|
| `teacherId` | Get all courses assigned to a teacher |
| `courseId` | Get all teachers assigned to a course |

```bash
# All courses for a teacher
curl "http://localhost:8082/api/courses/teachers?teacherId=teacher-uuid"

# All teachers for a course
curl "http://localhost:8082/api/courses/teachers?courseId=10"
```

**Success Response (200) — by teacherId:**
```json
[
  {
    "courseId": 10,
    "teacherId": "teacher-uuid",
    "course": {
      "id": 10,
      "title": "Cybersecurity Fundamentals",
      "category": "Cybersecurity",
      "isActive": true
    }
  }
]
```

**Success Response (200) — by courseId:**
```json
[
  {
    "courseId": 10,
    "teacherId": "teacher-uuid-1",
    "teacher": {
      "id": "teacher-uuid-1",
      "fullName": "Anand Kumar",
      "email": "anand@cyberlearnix.com"
    }
  }
]
```

---

### 4.3 POST — Assign Teacher to Course (Admin Only)
`POST /api/courses/teachers`

```bash
curl -X POST http://localhost:8082/api/courses/teachers \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "courseId": 10,
    "teacherId": "teacher-uuid"
  }'
```

**Success Response (200):**
```json
{ "message": "Teacher assigned successfully" }
```

**Error (403):** Not admin.  
**Error (409):** Teacher already assigned.

---

### 4.4 DELETE — Remove Teacher from Course (Admin Only)
`DELETE /api/courses/teachers`

```bash
curl -X DELETE http://localhost:8082/api/courses/teachers \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "courseId": 10,
    "teacherId": "teacher-uuid"
  }'
```

**Success Response (200):**
```json
{ "message": "Teacher removed successfully" }
```

---

## 5. Modules & Contents

**Base path:** `/api/course-management/modules` & `/api/course-management/courses/{courseId}/modules`

---

### 5.1 POST — Create Module
`POST /api/course-management/courses/{courseId}/modules`

**Required Headers:** `X-User-Id`, `X-User-Role`  
**Permission checks:** `canAddModules` + does not exceed `maxModulesPerCourse`

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Module title |
| `description` | string | No | Module description |
| `orderIndex` | integer | No | Position in course (auto-assigned if omitted) |

```bash
curl -X POST http://localhost:8082/api/course-management/courses/10/modules \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Introduction to Cybersecurity",
    "description": "Overview of security landscape",
    "orderIndex": 1
  }'
```

**Success Response (201):**
```json
{
  "id": 1,
  "courseId": 10,
  "title": "Introduction to Cybersecurity",
  "description": "Overview of security landscape",
  "orderIndex": 1,
  "isActive": true,
  "createdBy": "teacher-uuid",
  "createdAt": "2026-04-17T10:00:00",
  "updatedAt": "2026-04-17T10:00:00"
}
```

**Error (403):** `{ "error": "You do not have permission to add modules" }` or limit exceeded.

---

### 5.2 PUT — Update Module
`PUT /api/course-management/modules/{id}`

```bash
curl -X PUT http://localhost:8082/api/course-management/modules/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Introduction to Cybersecurity (Updated)",
    "description": "Revised overview",
    "orderIndex": 1
  }'
```

**Success Response (200):** Updated module object.  
**Error (403):** No `canEditModules` permission.

---

### 5.3 DELETE — Delete Module (Admin Only)
`DELETE /api/course-management/modules/{id}`

```bash
curl -X DELETE http://localhost:8082/api/course-management/modules/1 \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin"
```

**Success Response (200):** `{ "message": "Module deleted" }`  
**Error (403):** Not admin.

---

### 5.4 GET — List Module Contents
`GET /api/course-management/modules/{moduleId}/contents`

**Required Headers:** `X-User-Role` (admin or teacher only)

```bash
curl http://localhost:8082/api/course-management/modules/1/contents \
  -H "X-User-Role: teacher"
```

**Success Response (200):**
```json
[
  {
    "id": 101,
    "moduleId": 1,
    "title": "What is Cybersecurity?",
    "contentType": "LECTURE",
    "orderIndex": 1,
    "isActive": true,
    "status": "PENDING",
    "videoUrl": "https://res.cloudinary.com/.../video.mp4",
    "durationMinutes": 15,
    "isPreview": true,
    "contentText": null,
    "attachmentUrl": null
  },
  {
    "id": 102,
    "moduleId": 1,
    "title": "Week 1 Lab — Recon",
    "contentType": "LAB",
    "orderIndex": 2,
    "isActive": true,
    "labType": "HANDS_ON",
    "instructions": "Use nmap to scan the target...",
    "durationMinutes": 60,
    "difficultyLevel": "BEGINNER"
  }
]
```

---

### 5.5 POST — Create Content Item
`POST /api/course-management/modules/{moduleId}/contents`

Creates a typed content item inside a module. The `contentType` field controls which subtype is created, and which extra fields are saved.

**Required Headers:** `X-User-Id`, `X-User-Role`  
**Permission checks:** `canAddContent` (+ `canManageExams` for QUIZ/EXAM types), `maxContentPerModule` limit

**Request Body — Common Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Content title |
| `description` | string | No | Content description |
| `contentType` | string | Yes | `LECTURE`, `VIDEO`, `LAB`, `ASSIGNMENT`, `QUIZ`, `EXAM` |
| `orderIndex` | integer | No | Position in module |

**Extra fields for `LECTURE` / `VIDEO`:**

| Field | Type | Description |
|-------|------|-------------|
| `videoUrl` | string | Cloudinary video URL |
| `contentText` | string | Rich text / notes |
| `durationMinutes` | integer | Video length |
| `isPreview` | boolean | Whether non-enrolled users can watch |
| `attachmentUrl` | string | Downloadable file URL |
| `interactiveUrl` | string | External interactive content URL |

**Extra fields for `LAB`:**

| Field | Type | Description |
|-------|------|-------------|
| `labType` | string | `HANDS_ON`, `SIMULATION`, `VIRTUAL_LAB` |
| `instructions` | string | Lab instructions (rich text) |
| `environmentConfig` | string (JSON) | Lab environment setup config |
| `durationMinutes` | integer | Expected completion time |
| `difficultyLevel` | string | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `prerequisites` | string | What student should know beforehand |
| `learningObjectives` | string | What student will learn |
| `hasSolution` | boolean | Whether solution guide is available |
| `solutionGuide` | string | Solution walkthrough text |

**Extra fields for `ASSIGNMENT`:**

| Field | Type | Description |
|-------|------|-------------|
| `assignmentType` | string | `PROJECT`, `CASE_STUDY`, `RESEARCH`, `CODING` |
| `instructions` | string | Assignment instructions |
| `requirements` | string | What must be submitted |
| `submissionFormat` | string | `PDF`, `CODE`, `VIDEO`, `LINK` |
| `maxScore` | integer | Maximum score (default 100) |
| `dueDate` | string (ISO 8601) | Submission deadline |
| `lateSubmissionAllowed` | boolean | Allow late submissions |
| `latePenaltyPercent` | integer | Score penalty % for late submission |
| `rubric` | string (JSON) | Grading rubric |
| `autoGrade` | boolean | Auto-grade on submission |
| `plagiarismCheck` | boolean | Run plagiarism detection |

**Extra fields for `QUIZ` / `EXAM`:**

| Field | Type | Description |
|-------|------|-------------|
| `quizId` | string | External quiz reference ID |
| `timeLimitMinutes` | integer | Time allowed |
| `passingScore` | integer | Minimum % to pass (default 70) |
| `maxAttempts` | integer | Max retries (default 3) |

---

**Example — Create a LECTURE:**
```bash
curl -X POST http://localhost:8082/api/course-management/modules/1/contents \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Introduction to Networking Concepts",
    "description": "Core networking theory for beginners",
    "contentType": "LECTURE",
    "orderIndex": 1,
    "videoUrl": "https://res.cloudinary.com/dt6rxrpqr/video/upload/v1/intro.mp4",
    "durationMinutes": 22,
    "isPreview": false,
    "contentText": "<p>In this lecture we cover OSI model...</p>"
  }'
```

**Example — Create a LAB:**
```bash
curl -X POST http://localhost:8082/api/course-management/modules/1/contents \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Nmap Network Scanning Lab",
    "contentType": "LAB",
    "orderIndex": 2,
    "labType": "HANDS_ON",
    "instructions": "Connect to the lab VM and use nmap to discover open ports on target 192.168.1.0/24",
    "durationMinutes": 45,
    "difficultyLevel": "BEGINNER",
    "prerequisites": "Basic Linux terminal knowledge",
    "learningObjectives": "Master nmap scanning techniques",
    "hasSolution": true,
    "solutionGuide": "Run: nmap -sV -p- 192.168.1.1"
  }'
```

**Example — Create an ASSIGNMENT:**
```bash
curl -X POST http://localhost:8082/api/course-management/modules/2/contents \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Security Audit Report",
    "contentType": "ASSIGNMENT",
    "orderIndex": 3,
    "assignmentType": "PROJECT",
    "instructions": "Perform a security audit on the provided sample company network",
    "requirements": "Submit a professional PDF report with findings and recommendations",
    "submissionFormat": "PDF",
    "maxScore": 100,
    "dueDate": "2026-05-01T23:59:00",
    "lateSubmissionAllowed": true,
    "latePenaltyPercent": 10,
    "plagiarismCheck": true
  }'
```

**Example — Create a QUIZ:**
```bash
curl -X POST http://localhost:8082/api/course-management/modules/1/contents \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Module 1 Quiz",
    "contentType": "QUIZ",
    "orderIndex": 4,
    "timeLimitMinutes": 30,
    "passingScore": 70,
    "maxAttempts": 3
  }'
```

**Success Response (201):** The created content object with all fields populated.

```json
{
  "id": 105,
  "moduleId": 1,
  "title": "Module 1 Quiz",
  "contentType": "QUIZ",
  "orderIndex": 4,
  "isActive": true,
  "status": "PENDING",
  "timeLimitMinutes": 30,
  "passingScore": 70,
  "maxAttempts": 3,
  "createdBy": "teacher-uuid",
  "createdAt": "2026-04-17T10:00:00"
}
```

**Error (403):** No permission or limit reached:
```json
{ "error": "You have reached the maximum content limit for this module" }
```

---

### 5.6 PUT — Update Content
`PUT /api/course-management/contents/{id}`

Same request body as POST. Checks `canEditContent` permission.

```bash
curl -X PUT http://localhost:8082/api/course-management/contents/101 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{
    "title": "Introduction to Networking (Updated)",
    "contentType": "LECTURE",
    "durationMinutes": 25,
    "isPreview": true
  }'
```

**Success Response (200):** Updated content object.

---

### 5.7 DELETE — Delete Content (Admin Only)
`DELETE /api/course-management/contents/{id}`

```bash
curl -X DELETE http://localhost:8082/api/course-management/contents/101 \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin"
```

**Success Response (200):** `{ "message": "Content deleted" }`

---

## 6. Quiz Questions

**Base path:** `/api/course-management/contents/{contentId}/quiz/questions`

Add questions to a quiz/exam content item.

### 6.1 POST — Bulk Add Questions
`POST /api/course-management/contents/{contentId}/quiz/questions`

Adds multiple questions and their options to a quiz in one call.

**Request Body:** Array of question objects.

| Question Field | Type | Required | Description |
|---------------|------|----------|-------------|
| `questionText` | string | Yes | The question text |
| `questionType` | string | Yes | `MCQ` (single answer), `MSQ` (multiple answers), `TRUE_FALSE` |
| `points` | integer | No | Points for this question (default 1) |
| `explanation` | string | No | Explanation shown after answering |
| `options` | array | Yes | Array of option objects |

| Option Field | Type | Required | Description |
|-------------|------|----------|-------------|
| `optionText` | string | Yes | Option display text |
| `isCorrect` | boolean | Yes | Whether this is the correct answer |

```bash
curl -X POST http://localhost:8082/api/course-management/contents/105/quiz/questions \
  -H "Content-Type: application/json" \
  -H "X-User-Role: teacher" \
  -d '[
    {
      "questionText": "What does OSI stand for?",
      "questionType": "MCQ",
      "points": 2,
      "explanation": "OSI stands for Open Systems Interconnection",
      "options": [
        { "optionText": "Open Systems Interconnection", "isCorrect": true },
        { "optionText": "Open Software Interface", "isCorrect": false },
        { "optionText": "Operational System Integration", "isCorrect": false },
        { "optionText": "None of the above", "isCorrect": false }
      ]
    },
    {
      "questionText": "Which of the following are network layer protocols? (Select all that apply)",
      "questionType": "MSQ",
      "points": 3,
      "options": [
        { "optionText": "IP", "isCorrect": true },
        { "optionText": "ICMP", "isCorrect": true },
        { "optionText": "HTTP", "isCorrect": false },
        { "optionText": "FTP", "isCorrect": false }
      ]
    },
    {
      "questionText": "TCP is a connection-oriented protocol.",
      "questionType": "TRUE_FALSE",
      "points": 1,
      "options": [
        { "optionText": "True", "isCorrect": true },
        { "optionText": "False", "isCorrect": false }
      ]
    }
  ]'
```

**Success Response (200):** Array of saved question objects with IDs:
```json
[
  {
    "id": 1,
    "quizId": 105,
    "questionText": "What does OSI stand for?",
    "questionType": "MCQ",
    "points": 2,
    "orderIndex": 1,
    "explanation": "OSI stands for Open Systems Interconnection",
    "options": [
      { "id": 1, "optionText": "Open Systems Interconnection", "isCorrect": true },
      { "id": 2, "optionText": "Open Software Interface", "isCorrect": false },
      { "id": 3, "optionText": "Operational System Integration", "isCorrect": false },
      { "id": 4, "optionText": "None of the above", "isCorrect": false }
    ]
  }
]
```

---

## 7. Progress Tracking

**Base path:** `/api/courses/progress`

Tracks per-student, per-content-item progress. Automatically updates the overall course progress percentage (synced to enrollment-service).

---

### 7.1 POST — Update Content Progress
`POST /api/courses/progress/update`

Call this every time a student interacts with a content item: starts a video, completes a lab, submits a quiz.

**Required Headers:** `X-User-Id` (the student's ID)

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contentId` | number | Yes | The content item ID |
| `status` | string | Yes | `STARTED` or `COMPLETED` |
| `videoTime` | integer | No | Current playback position in seconds (for video lecture resume) |
| `score` | number | No | Quiz/assignment score (0-100) |

```bash
# Student starts watching a video
curl -X POST http://localhost:8082/api/courses/progress/update \
  -H "Content-Type: application/json" \
  -H "X-User-Id: student-uuid" \
  -d '{
    "contentId": 101,
    "status": "STARTED",
    "videoTime": 0
  }'

# Student resumes video — save position
curl -X POST http://localhost:8082/api/courses/progress/update \
  -H "Content-Type: application/json" \
  -H "X-User-Id: student-uuid" \
  -d '{
    "contentId": 101,
    "status": "STARTED",
    "videoTime": 384
  }'

# Student completes a video
curl -X POST http://localhost:8082/api/courses/progress/update \
  -H "Content-Type: application/json" \
  -H "X-User-Id: student-uuid" \
  -d '{
    "contentId": 101,
    "status": "COMPLETED"
  }'

# Student completes a quiz with a score
curl -X POST http://localhost:8082/api/courses/progress/update \
  -H "Content-Type: application/json" \
  -H "X-User-Id: student-uuid" \
  -d '{
    "contentId": 105,
    "status": "COMPLETED",
    "score": 85.0
  }'
```

**Success Response (200):**
```json
{
  "id": 500,
  "studentId": "student-uuid",
  "contentId": 101,
  "status": "COMPLETED",
  "isCompleted": true,
  "lastAccessedAt": "2026-04-17T11:00:00",
  "completedAt": "2026-04-17T11:00:00",
  "videoTimeSeconds": null,
  "score": null,
  "overallCourseProgress": 35
}
```

> **`overallCourseProgress`** is the updated % synced to the enrollment-service.

---

### 7.2 GET — Get Course Progress for Student
`GET /api/courses/progress/{courseId}`

Returns completion status for every content item in a course, plus the overall % from enrollment-service.

**Required Headers:** `X-User-Id` (the student's ID)

```bash
curl http://localhost:8082/api/courses/progress/10 \
  -H "X-User-Id: student-uuid"
```

**Success Response (200):**
```json
{
  "courseId": 10,
  "overallProgress": 35,
  "contentProgress": [
    {
      "contentId": 101,
      "status": "COMPLETED",
      "isCompleted": true,
      "lastAccessedAt": "2026-04-17T11:00:00",
      "completedAt": "2026-04-17T11:00:00",
      "videoTimeSeconds": null,
      "score": null
    },
    {
      "contentId": 102,
      "status": "STARTED",
      "isCompleted": false,
      "lastAccessedAt": "2026-04-17T10:30:00",
      "completedAt": null,
      "videoTimeSeconds": 384,
      "score": null
    },
    {
      "contentId": 103,
      "status": null,
      "isCompleted": false,
      "lastAccessedAt": null,
      "completedAt": null,
      "videoTimeSeconds": null,
      "score": null
    }
  ]
}
```

> **`status: null`** means the student hasn't started that content yet.  
> **`videoTimeSeconds`** — use this to resume video playback from the last position.

---

## 8. Material Uploads

**Base path:** `/api/materials`  
**Content-Type:** `multipart/form-data`  
**Form field name:** `file`

All upload endpoints use Cloudinary for storage and return a hosted URL.

| Endpoint | Who Can Upload | Allowed Types | Max Size |
|----------|---------------|--------------|----------|
| `POST /api/materials/upload/thumbnail` | Any logged-in user | Images only (jpg, png, webp, gif) | 5 MB |
| `POST /api/materials/upload/video` | Admin, Teacher, Dual | Video only (mp4, mov, mkv, webm) | 500 MB |
| `POST /api/materials/upload/document` | Admin, Teacher, Dual | Any file type | 50 MB |
| `POST /api/materials/upload/banner` | Admin only | Images only | No explicit limit |

---

### 8.1 Upload Thumbnail
```bash
curl -X POST http://localhost:8082/api/materials/upload/thumbnail \
  -H "X-User-Id: teacher-uuid" \
  -F "file=@/path/to/thumbnail.jpg"
```

**Success Response (200):**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/v1713340800/thumbnail.jpg"
}
```

**Error (400) — wrong file type:**
```json
{ "success": false, "error": "Only image files are allowed" }
```

**Error (400) — file too large:**
```json
{ "success": false, "error": "File size exceeds 5MB limit" }
```

---

### 8.2 Upload Video
```bash
curl -X POST http://localhost:8082/api/materials/upload/video \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -F "file=@/path/to/lecture.mp4"
```

**Success Response (200):**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/video/upload/v1713340800/lecture.mp4"
}
```

---

### 8.3 Upload Document
```bash
curl -X POST http://localhost:8082/api/materials/upload/document \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -F "file=@/path/to/lecture-notes.pdf"
```

**Success Response (200):**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/raw/upload/v1713340800/lecture-notes.pdf"
}
```

---

### 8.4 Upload Banner Image (Admin Only)
```bash
curl -X POST http://localhost:8082/api/materials/upload/banner \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin" \
  -F "file=@/path/to/banner.jpg"
```

**Success Response (200):**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/v1713340800/banner.jpg"
}
```

---

## 9. Banners

**Base path:** `/api/banners`

Homepage hero/slider banners. Ordered by `displayOrder` ascending.

---

### 9.1 GET — List All Banners (Public)
```bash
curl http://localhost:8082/api/banners
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "title": "Learn Cybersecurity",
    "subtitle": "Start your journey with industry-leading courses",
    "imgUrl": "https://res.cloudinary.com/.../banner1.jpg",
    "buttons": "[{\"label\":\"Explore Courses\",\"url\":\"/courses\",\"type\":\"primary\"},{\"label\":\"Learn More\",\"url\":\"/about\",\"type\":\"secondary\"}]",
    "displayOrder": 1,
    "createdAt": "2026-01-01T00:00:00"
  }
]
```

> **`buttons`** is a JSON string — parse it with `JSON.parse(banner.buttons)` on the frontend.

---

### 9.2 POST — Create Banner (Admin)
```bash
curl -X POST http://localhost:8082/api/banners \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "New Batch Starting Soon",
    "subtitle": "Limited seats available — register now",
    "imgUrl": "https://res.cloudinary.com/.../banner2.jpg",
    "buttons": "[{\"label\":\"Register\",\"url\":\"/enroll\",\"type\":\"primary\"}]",
    "displayOrder": 2
  }'
```

**Success Response (201):** Created banner object.

---

### 9.3 PUT — Update Banner (Admin)
```bash
curl -X PUT http://localhost:8082/api/banners/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Updated Title",
    "subtitle": "Updated subtitle",
    "imgUrl": "https://res.cloudinary.com/.../new-banner.jpg",
    "displayOrder": 1
  }'
```

**Success Response (200):** Updated banner object.

---

### 9.4 DELETE — Delete Banner (Admin)
```bash
curl -X DELETE http://localhost:8082/api/banners/1 \
  -H "X-User-Role: admin"
```

**Success Response (200):** `{ "message": "Banner deleted" }`

---

## 10. Promo Banners

**Base path:** `/api/promos`

Smaller promotional banners (sidebar or pop-up style). Can be enabled/disabled via `status` field.

---

### 10.1 GET — List All Promos (Public)
```bash
curl http://localhost:8082/api/promos
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "title": "25% Off this Weekend",
    "description": "Use code CYBER25 at checkout",
    "imgUrl": "https://res.cloudinary.com/.../promo1.jpg",
    "link": "/courses/10",
    "status": "active",
    "createdAt": "2026-04-01T00:00:00"
  }
]
```

---

### 10.2 POST — Create Promo (Admin)
```bash
curl -X POST http://localhost:8082/api/promos \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Free Webinar",
    "description": "Join our free live webinar on network security",
    "imgUrl": "https://res.cloudinary.com/.../promo2.jpg",
    "link": "/webinar",
    "status": "active"
  }'
```

**Success Response (201):** Created promo object.

---

### 10.3 PUT — Update Promo
```bash
curl -X PUT http://localhost:8082/api/promos/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -d '{
    "title": "Updated Promo",
    "status": "inactive"
  }'
```

**Success Response (200):** Updated promo object.

---

### 10.4 DELETE — Delete Promo
```bash
curl -X DELETE http://localhost:8082/api/promos/1 \
  -H "X-User-Role: admin"
```

**Success Response (200):** `{ "message": "Promo deleted" }`

---

## 11. Certificates

**Base path:** `/api/certificates`

Issue and manage course completion certificates.

---

### 11.1 GET — List All Certificates
```bash
curl http://localhost:8082/api/certificates
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "studentName": "Rahul Singh",
    "studentDob": "1998-05-15",
    "type": "COMPLETION",
    "courseName": "Cybersecurity Fundamentals",
    "issueDate": "2026-04-01",
    "certificateId": "CERT-2026-001",
    "createdAt": "2026-04-01T09:00:00"
  }
]
```

---

### 11.2 POST — Issue Certificate
```bash
curl -X POST http://localhost:8082/api/certificates \
  -H "Content-Type: application/json" \
  -d '{
    "studentName": "Rahul Singh",
    "studentDob": "1998-05-15",
    "type": "COMPLETION",
    "courseName": "Cybersecurity Fundamentals",
    "issueDate": "2026-04-17",
    "certificateId": "CERT-2026-010"
  }'
```

**Success Response (201):** Created certificate object.

---

### 11.3 DELETE — Delete Certificate
```bash
curl -X DELETE http://localhost:8082/api/certificates/1
```

**Success Response (200):** `{ "message": "Certificate deleted" }`

---

### 11.4 GET — List Certificate Templates
```bash
curl http://localhost:8082/api/certificates/templates
```

**Success Response (200):**
```json
[
  { "id": 1, "type": "COMPLETION", "backgroundUrl": "https://res.cloudinary.com/.../cert-bg.jpg" },
  { "id": 2, "type": "EXCELLENCE", "backgroundUrl": "https://res.cloudinary.com/.../cert-excellence.jpg" }
]
```

---

### 11.5 PUT — Update Certificate Template
```bash
curl -X PUT http://localhost:8082/api/certificates/templates/1 \
  -H "Content-Type: application/json" \
  -d '{ "backgroundUrl": "https://res.cloudinary.com/.../new-bg.jpg" }'
```

**Success Response (200):** Updated template object.

---

## 12. Content Partners

**Base path:** `/api/partners`

Technology or content partner logos shown on homepage or course pages.

---

### 12.1 GET — List All Partners (Public)
```bash
curl http://localhost:8082/api/partners
```

**Success Response (200):**
```json
{
  "success": true,
  "partners": [
    {
      "id": 1,
      "name": "EC-Council",
      "url": "https://www.eccouncil.org",
      "logo_url": "https://res.cloudinary.com/.../ec-council-logo.png",
      "createdAt": "2026-01-01T00:00:00"
    }
  ]
}
```

> **Note:** The logo field is `logo_url` (not camelCase) — use exactly this key.

---

### 12.2 POST — Create Partner
```bash
curl -X POST http://localhost:8082/api/partners \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Cisco",
    "url": "https://www.cisco.com",
    "logo_url": "https://res.cloudinary.com/.../cisco-logo.png"
  }'
```

**Success Response (201):** Created partner object.

---

### 12.3 PUT — Update Partner
```bash
curl -X PUT http://localhost:8082/api/partners/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Name",
    "url": "https://new-url.com",
    "logo_url": "https://res.cloudinary.com/.../new-logo.png"
  }'
```

**Success Response (200):** Updated partner object.

---

### 12.4 DELETE — Delete Partner
```bash
curl -X DELETE http://localhost:8082/api/partners/1
```

**Success Response (200):** `{ "message": "Partner deleted" }`

---

## 13. Course Suggestions

**Base path:** `/api/suggestions`

Admins create suggestions for courses (e.g., "Add more labs to this course"). Teachers can view and resolve suggestions for their courses.

---

### 13.1 GET — List Suggestions
`GET /api/suggestions`

**Required Headers:** `X-User-Id`, `X-User-Role`

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `courseId` | No | Filter suggestions for a specific course |

```bash
# Admin: all suggestions
curl http://localhost:8082/api/suggestions \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin"

# Teacher: only their course suggestions
curl http://localhost:8082/api/suggestions \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher"

# Filter by course
curl "http://localhost:8082/api/suggestions?courseId=10" \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin"
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "courseId": 10,
    "adminId": "admin-uuid",
    "suggestion": "Please add more practical labs to Module 2",
    "status": "pending",
    "createdAt": "2026-04-15T10:00:00"
  }
]
```

---

### 13.2 POST — Create Suggestion (Admin Only)
```bash
curl -X POST http://localhost:8082/api/suggestions \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin-uuid" \
  -H "X-User-Role: admin" \
  -d '{
    "courseId": 10,
    "suggestion": "Add a quiz at the end of every module"
  }'
```

**Success Response (201):** Created suggestion object.

---

### 13.3 PUT — Update Suggestion Status
```bash
curl -X PUT http://localhost:8082/api/suggestions/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher" \
  -d '{ "status": "resolved" }'
```

**Success Response (200):** Updated suggestion object.

---

## 14. Content Updates (News Feed)

**Base path:** `/api/updates`

"What's New" news items — updates shown on the student/teacher dashboard (new courses, features, announcements).

---

### 14.1 GET — List All Updates (Public)
```bash
curl http://localhost:8082/api/updates
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "title": "New Course Added: AI Masterclass",
    "description": "Our most requested course is now live!",
    "icon": "🎓",
    "link": "/courses/15",
    "btnText": "Enroll Now",
    "imgUrl": "https://res.cloudinary.com/.../update1.jpg",
    "details": "Full course details...",
    "createdAt": "2026-04-17T09:00:00"
  }
]
```

---

### 14.2 POST — Create Update
```bash
curl -X POST http://localhost:8082/api/updates \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Platform Maintenance Window",
    "description": "Scheduled maintenance on Sunday 2-4 AM IST",
    "icon": "🔧",
    "link": null,
    "btnText": null,
    "details": "The platform will be unavailable briefly..."
  }'
```

**Success Response (201):** Created update object.

---

### 14.3 PUT — Update a News Item
```bash
curl -X PUT http://localhost:8082/api/updates/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "btnText": "Learn More"
  }'
```

**Success Response (200):** Updated item object.

---

### 14.4 DELETE — Delete Update
```bash
curl -X DELETE http://localhost:8082/api/updates/1
```

**Success Response (200):** `{ "message": "Update deleted" }`

---

## 15. Content Reviews

**Base path:** `/api/content-reviews`

Workflow for teacher-submitted content to go through admin review before being published.

**Review Status Values:**

| Status | Meaning |
|--------|---------|
| `pending` | Submitted, awaiting admin review |
| `approved` | Content approved, ready to publish |
| `rejected` | Content rejected |
| `revision_required` | Admin wants changes before approving |

---

### 15.1 GET — List Reviews
`GET /api/content-reviews`

**Query Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `status` | No | Filter by review status (`pending`, `approved`, `rejected`, `revision_required`) |
| `teacherId` | No | Filter by teacher ID (number) |

```bash
# All reviews
curl http://localhost:8082/api/content-reviews

# Only pending reviews
curl "http://localhost:8082/api/content-reviews?status=pending"

# Reviews submitted by a specific teacher
curl "http://localhost:8082/api/content-reviews?teacherId=42"
```

**Success Response (200):**
```json
[
  {
    "id": 1,
    "contentId": 101,
    "contentType": "LECTURE",
    "teacherId": 42,
    "reviewStatus": "pending",
    "content": "Video lecture on OSI model...",
    "metadata": { "duration": 22, "videoUrl": "https://..." },
    "submittedAt": "2026-04-16T10:00:00",
    "reviewedAt": null,
    "reviewerId": null,
    "reviewNotes": null,
    "isApproved": false,
    "requiresRevision": false,
    "revisionNotes": null
  }
]
```

---

### 15.2 GET — Pending Reviews Only
```bash
curl http://localhost:8082/api/content-reviews/pending
```

**Success Response (200):** Array of reviews with `reviewStatus: "pending"`.

---

### 15.3 POST — Submit Content for Review
```bash
curl -X POST http://localhost:8082/api/content-reviews \
  -H "Content-Type: application/json" \
  -d '{
    "contentId": 101,
    "contentType": "LECTURE",
    "teacherId": 42,
    "content": "Video lecture covering OSI model layers 1-7",
    "metadata": { "duration": 22 }
  }'
```

**Success Response (201):** Review object with `reviewStatus: "pending"`.

---

### 15.4 PATCH — Perform Review (Admin)
`PATCH /api/content-reviews/{id}/review`

```bash
# Approve
curl -X PATCH http://localhost:8082/api/content-reviews/1/review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "notes": "Great content, well structured",
    "approved": true,
    "requiresRevision": false
  }'

# Request revision
curl -X PATCH http://localhost:8082/api/content-reviews/1/review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "notes": "Video quality is too low",
    "approved": false,
    "requiresRevision": true,
    "revisionNotes": "Please re-record at 1080p minimum"
  }'

# Reject
curl -X PATCH http://localhost:8082/api/content-reviews/1/review \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "notes": "Content does not meet curriculum requirements",
    "approved": false,
    "requiresRevision": false
  }'
```

**Success Response (200):** Updated review object with `reviewStatus` set to `approved`, `rejected`, or `revision_required`.

---

### 15.5 PATCH — Resubmit Revised Content
`PATCH /api/content-reviews/{id}/revise`

After teacher makes changes based on revision notes.

```bash
curl -X PATCH http://localhost:8082/api/content-reviews/1/revise \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Updated video lecture at 1080p quality - OSI model..."
  }'
```

**Success Response (200):** Review updated with `reviewStatus: "pending"` (back in queue).

---

### 15.6 DELETE — Delete Review
```bash
curl -X DELETE http://localhost:8082/api/content-reviews/1
```

**Success Response (200):** `{ "message": "Review deleted" }`

---

## 16. Teacher Dashboard

**Base path:** `/api/teacher`

**Access:** Roles `teacher`, `dual`, or `admin` only.

### 16.1 GET — Teacher Dashboard Stats
`GET /api/teacher/dashboard`

```bash
curl http://localhost:8082/api/teacher/dashboard \
  -H "X-User-Id: teacher-uuid" \
  -H "X-User-Role: teacher"
```

**Success Response (200):**
```json
{
  "teacherId": "teacher-uuid",
  "createdCourses": 3,
  "assignedCourses": 2,
  "totalManagedCourses": 5,
  "activeCourses": 4,
  "pendingReview": 1,
  "publishedCourses": 4
}
```

| Field | Description |
|-------|-------------|
| `createdCourses` | Courses this teacher originally created |
| `assignedCourses` | Courses admin assigned this teacher to (but didn't create) |
| `totalManagedCourses` | created + assigned |
| `activeCourses` | Courses with `isActive: true` |
| `pendingReview` | Courses in `PENDING` status waiting admin approval |
| `publishedCourses` | Courses in `APPROVED` status |

**Error (403):** Not a teacher/dual/admin.

---

## 17. Admin Stats — Courses

**Base path:** `/api/admin/stats`

**Access:** Admin role only.

### 17.1 GET — Course Statistics
`GET /api/admin/stats/courses`

```bash
curl http://localhost:8082/api/admin/stats/courses \
  -H "Authorization: Bearer <admin_jwt>"
```

**Success Response (200):**
```json
{
  "totalCourses": 25,
  "activeCourses": 20,
  "inactiveCourses": 5,
  "pendingReview": 3,
  "publishedCourses": 17
}
```

---

## 18. WebSocket Real-Time Events

**Endpoint:** `ws://localhost:8082/ws/course-management`  
**Protocol:** STOMP over WebSocket (SockJS supported)

Use this for live updates on the course editor and student learning pages.

---

### Subscribe Topics

| Topic | When It Fires | Payload |
|-------|--------------|---------|
| `/topic/course/{courseId}` | Course/module/content is updated | `{ courseId, type, message }` |
| `/topic/course-updates` | Any course updated | `{ courseId, updateType, userId }` |
| `/topic/module-updates` | Any module updated | `{ moduleId, courseId, updateType, userId }` |
| `/topic/content-updates` | Any content updated | `{ contentId, moduleId, courseId, updateType, userId }` |
| `/user/{userId}/queue/auth-status` | Auth token validated | `{ valid, userId, role }` |

---

### Send Messages

**Authenticate over WebSocket:**
```javascript
stompClient.send("/app/auth", {}, JSON.stringify({
  userId: "student-uuid",
  userRole: "student",
  token: "Bearer eyJhbGci..."
}));
```

**Notify course update (from teacher/admin side):**
```javascript
stompClient.send("/app/course/update", {}, JSON.stringify({
  courseId: 10,
  updateType: "CONTENT_ADDED",
  userId: "teacher-uuid"
}));
```

---

### JavaScript Frontend Setup Example
```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8082/ws/course-management'),
  onConnect: () => {
    // Subscribe to live course updates
    client.subscribe('/topic/course/10', (message) => {
      const update = JSON.parse(message.body);
      console.log('Course updated:', update);
      // Refresh course content in UI
    });
  }
});
client.activate();
```

---

## 19. Data Models Reference

### Course Object
```json
{
  "id": 10,
  "title": "string",
  "description": "string",
  "category": "string",
  "difficultyLevel": "BEGINNER | INTERMEDIATE | ADVANCED",
  "duration": "string (e.g. '12h 30m')",
  "contentUrl": "string | null",
  "thumbnailUrl": "string (Cloudinary URL) | null",
  "basePrice": 4999.0,
  "gstPercent": 18,
  "finalPrice": 5898.82,
  "isActive": true,
  "status": "APPROVED | PENDING | REJECTED",
  "createdBy": "user-uuid",
  "createdAt": "2026-01-10T10:00:00",
  "updatedAt": "2026-04-01T09:00:00"
}
```

### Module Object
```json
{
  "id": 1,
  "courseId": 10,
  "title": "string",
  "description": "string",
  "orderIndex": 1,
  "isActive": true,
  "createdBy": "user-uuid",
  "createdAt": "2026-01-10T10:00:00",
  "updatedAt": "2026-01-10T10:00:00"
}
```

### Content Item Objects (by type)

**LECTURE / VIDEO:**
```json
{
  "id": 101, "moduleId": 1, "title": "string",
  "contentType": "LECTURE",
  "orderIndex": 1, "isActive": true, "status": "PENDING | APPROVED",
  "videoUrl": "https://cloudinary.com/...",
  "contentText": "rich text | null",
  "durationMinutes": 22,
  "isPreview": false,
  "attachmentUrl": "https://... | null",
  "interactiveUrl": "https://... | null"
}
```

**LAB:**
```json
{
  "id": 102, "moduleId": 1, "title": "string",
  "contentType": "LAB",
  "labType": "HANDS_ON | SIMULATION | VIRTUAL_LAB",
  "instructions": "string",
  "environmentConfig": "JSON string | null",
  "durationMinutes": 60,
  "difficultyLevel": "BEGINNER | INTERMEDIATE | ADVANCED",
  "prerequisites": "string | null",
  "learningObjectives": "string | null",
  "hasSolution": true,
  "solutionGuide": "string | null"
}
```

**ASSIGNMENT:**
```json
{
  "id": 103, "moduleId": 1, "title": "string",
  "contentType": "ASSIGNMENT",
  "assignmentType": "PROJECT | CASE_STUDY | RESEARCH | CODING",
  "instructions": "string",
  "requirements": "string | null",
  "submissionFormat": "PDF | CODE | VIDEO | LINK",
  "maxScore": 100,
  "dueDate": "2026-05-01T23:59:00",
  "lateSubmissionAllowed": true,
  "latePenaltyPercent": 10,
  "rubric": "JSON string | null",
  "autoGrade": false,
  "plagiarismCheck": true
}
```

**QUIZ / EXAM:**
```json
{
  "id": 105, "moduleId": 1, "title": "string",
  "contentType": "QUIZ",
  "quizId": "string | null",
  "timeLimitMinutes": 30,
  "passingScore": 70,
  "maxAttempts": 3,
  "questions": [...]
}
```

---

## 20. Error Reference

| Status | Meaning | Frontend Action |
|--------|---------|----------------|
| `200` | Success | Process response |
| `201` | Created | Show success, redirect or update UI |
| `400` | Bad request (invalid input) | Show `error` or `message` from response body |
| `403` | Forbidden (wrong role / no permission) | Show "Permission denied" or redirect to login |
| `404` | Resource not found | Show "Not found" message |
| `409` | Conflict (duplicate — e.g. teacher already assigned) | Show `message` from response |
| `500` | Server error | Show "Something went wrong, try again" |

**Standard error body shape:**
```json
{
  "error": "Short error label",
  "message": "Human-readable explanation"
}
```

---

## Quick Reference — All Endpoints

| # | Method | Path | Auth Required | Description |
|---|--------|------|--------------|-------------|
| 1 | GET | `/api/courses` | No (role-based results) | List courses |
| 2 | GET | `/api/courses?id={id}` | No | Get single course |
| 3 | GET | `/api/courses/{id}/curriculum` | No | Public curriculum preview |
| 4 | POST | `/api/courses` | Admin/Teacher | Create course |
| 5 | PUT | `/api/courses/{id}` | Admin/Teacher | Update course |
| 6 | DELETE | `/api/courses/{id}` | Admin/Teacher | Delete course |
| 7 | POST | `/api/course-management/courses` | Admin/Teacher | Create course (management) |
| 8 | PUT | `/api/course-management/courses/{id}` | Admin/Teacher | Update course (management) |
| 9 | DELETE | `/api/course-management/courses/{id}` | Admin | Delete course (management) |
| 10 | GET | `/api/course-management/courses/{id}/full` | Admin/Teacher | Course with all modules+contents |
| 11 | GET | `/api/course-management/courses/{id}/students` | Admin/Teacher | Enrolled students with progress |
| 12 | GET | `/api/course-management/teacher-permissions/{id}` | Admin | Get teacher permissions |
| 13 | PUT | `/api/course-management/teacher-permissions/{id}` | Admin | Update teacher permissions |
| 14 | POST | `/api/course-management/courses/{id}/modules` | Admin/Teacher | Create module |
| 15 | PUT | `/api/course-management/modules/{id}` | Admin/Teacher | Update module |
| 16 | DELETE | `/api/course-management/modules/{id}` | Admin | Delete module |
| 17 | GET | `/api/course-management/modules/{id}/contents` | Admin/Teacher | List module contents |
| 18 | POST | `/api/course-management/modules/{id}/contents` | Admin/Teacher | Create content item |
| 19 | PUT | `/api/course-management/contents/{id}` | Admin/Teacher | Update content |
| 20 | DELETE | `/api/course-management/contents/{id}` | Admin | Delete content |
| 21 | POST | `/api/course-management/contents/{id}/quiz/questions` | Teacher | Bulk add quiz questions |
| 22 | GET | `/api/courses/teachers/exists` | Public | Check teacher-course assignment |
| 23 | GET | `/api/courses/teachers` | Public | List teacher-course assignments |
| 24 | POST | `/api/courses/teachers` | Admin | Assign teacher to course |
| 25 | DELETE | `/api/courses/teachers` | Admin | Remove teacher from course |
| 26 | POST | `/api/courses/progress/update` | Student | Update content progress |
| 27 | GET | `/api/courses/progress/{courseId}` | Student | Get course progress |
| 28 | POST | `/api/materials/upload/thumbnail` | Any logged-in | Upload thumbnail image |
| 29 | POST | `/api/materials/upload/video` | Teacher/Admin | Upload video |
| 30 | POST | `/api/materials/upload/document` | Teacher/Admin | Upload document |
| 31 | POST | `/api/materials/upload/banner` | Admin | Upload banner image |
| 32 | GET | `/api/banners` | Public | List banners |
| 33 | POST | `/api/banners` | Admin | Create banner |
| 34 | PUT | `/api/banners/{id}` | Admin | Update banner |
| 35 | DELETE | `/api/banners/{id}` | Admin | Delete banner |
| 36 | GET | `/api/promos` | Public | List promos |
| 37 | POST | `/api/promos` | Admin | Create promo |
| 38 | PUT | `/api/promos/{id}` | Admin | Update promo |
| 39 | DELETE | `/api/promos/{id}` | Admin | Delete promo |
| 40 | GET | `/api/certificates` | Public | List certificates |
| 41 | POST | `/api/certificates` | Admin | Issue certificate |
| 42 | DELETE | `/api/certificates/{id}` | Admin | Delete certificate |
| 43 | GET | `/api/certificates/templates` | Public | List cert templates |
| 44 | PUT | `/api/certificates/templates/{id}` | Admin | Update cert template |
| 45 | GET | `/api/partners` | Public | List partners |
| 46 | POST | `/api/partners` | Admin | Create partner |
| 47 | PUT | `/api/partners/{id}` | Admin | Update partner |
| 48 | DELETE | `/api/partners/{id}` | Admin | Delete partner |
| 49 | GET | `/api/suggestions` | Admin/Teacher | List suggestions |
| 50 | POST | `/api/suggestions` | Admin | Create suggestion |
| 51 | PUT | `/api/suggestions/{id}` | Admin/Teacher | Update suggestion status |
| 52 | GET | `/api/updates` | Public | List content updates |
| 53 | POST | `/api/updates` | Admin | Create update |
| 54 | PUT | `/api/updates/{id}` | Admin | Update news item |
| 55 | DELETE | `/api/updates/{id}` | Admin | Delete news item |
| 56 | GET | `/api/content-reviews` | Admin/Teacher | List reviews |
| 57 | GET | `/api/content-reviews/pending` | Admin | Pending reviews |
| 58 | POST | `/api/content-reviews` | Teacher | Submit for review |
| 59 | PATCH | `/api/content-reviews/{id}/review` | Admin | Perform review |
| 60 | PATCH | `/api/content-reviews/{id}/revise` | Teacher | Resubmit revision |
| 61 | DELETE | `/api/content-reviews/{id}` | Admin | Delete review |
| 62 | GET | `/api/teacher/dashboard` | Teacher/Dual/Admin | Teacher dashboard stats |
| 63 | GET | `/api/admin/stats/courses` | Admin | Course statistics |
| WS | STOMP | `ws://localhost:8082/ws/course-management` | Any | Real-time updates |
