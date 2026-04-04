# Cyberlearnix Course-Service API Documentation

This document providing technical details for the APIs in the `course-service` module (Port 8082).

---

## 1. Course Listing & Details (`/api/courses`)

Endpoints for discovering and viewing courses.

### 1.1 List All Courses
- **GET** `/api/courses`
- **Response (200 OK)**:
  ```json
  [
    {
      "id": 1,
      "title": "Intro to Ethical Hacking",
      "slug": "ethical-hacking",
      "description": "Short description...",
      "price": 199.99,
      "thumbnailUrl": "..."
    }
  ]
  ```

### 1.2 Get Specific Course
- **GET** `/api/courses/{slug_or_id}`
- **Response (200 OK)**: Full course details including curriculum/modules.

---

## 2. Course Management (Admin Only) (`/api/course-management`)

### 2.1 Create New Course
- **POST** `/api/course-management`
- **Request Body**:
  ```json
  {
    "title": "New Course",
    "description": "Full description",
    "shortDescription": "...",
    "price": 500,
    "difficulty": "Intermediate"
  }
  ```

### 2.2 Register Course Partner
- **POST** `/api/course-management/{courseId}/partners`
- **Request Body**: `{"partnerId": "UUID"}`

---

## 3. Student Progress & Learning (`/api/progress`)

### 3.1 Fetch My Progress
- **GET** `/api/progress/{courseId}` (Requires Auth Header)
- **Response**: `{"percentComplete": 45, "lastAccessedModule": 5}`

### 3.2 Update Lesson Completion
- **POST** `/api/progress/mark-complete`
- **Request Body**: `{"courseId": 1, "lessonId": 25}`

---

## 4. Materials & Uploads (`/api/materials`)

### 4.1 Upload Learning Material
- **POST** `/api/materials/upload`
- **Body**: `multipart/form-data` with keys `file`, `courseId`, `title`.

---

## 5. Marketing & Partners

### 5.1 Banners (`/api/banners`)
- **GET** `/api/banners`: List active marketing banners.
- **POST** `/api/banners`: (Admin) Add new banner.

### 5.2 Partners (`/api/partners`)
- **GET** `/api/partners`: List logos/names of industry partners.

---

## 6. Feedback & FAQ

### 6.1 Suggestions (`/api/suggestions`)
- **GET** `/api/suggestions`: List course suggestions.
- **POST** `/api/suggestions`: Submit a new suggestion.

### 6.2 Updates (`/api/updates`)
- **GET** `/api/updates`: List recent news or course update logs.
