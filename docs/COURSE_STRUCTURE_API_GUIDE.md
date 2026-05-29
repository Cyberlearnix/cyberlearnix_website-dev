# Course Structure API Guide
### Chapters, Sub-Chapters, and Content

> **Base URL:** `http://localhost:8080` (through gateway)  
> **All requests require:** `Authorization: Bearer <token>` header  
> **Allowed roles:** `admin`, `teacher`, `dual`

---

## Table of Contents
1. [Authentication](#1-authentication)
2. [Image Upload (do this first)](#2-image-upload-do-this-first)
3. [Chapters (Modules)](#3-chapters-modules)
4. [Sub-Chapters (Sub-Modules)](#4-sub-chapters-sub-modules)
5. [Content inside a Chapter/Sub-Chapter](#5-content-inside-a-chaptersub-chapter)
6. [Delete Endpoints](#6-delete-endpoints)
7. [Full Course Tree (read everything at once)](#7-full-course-tree-read-everything-at-once)
8. [Typical UI Workflow](#8-typical-ui-workflow)
9. [Response Shapes Reference](#9-response-shapes-reference)

---

## 1. Authentication

Login to get a token:

```
POST /api/auth/login
Content-Type: application/json

{
  "email": "shivakumar@cyberlearnix.com",
  "password": "Shivam$179"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "admin",
  "userId": "9f4ea44b-966c-4e76-9646-bae02bfc116b"
}
```

Use the `token` value in every subsequent request:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 2. Image Upload (do this first)

Upload an image **before** creating a chapter or content. Then paste the returned URL into `imageUrl`.

### Upload a chapter / sub-chapter cover image

```
POST /api/materials/upload/module-image
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <image file>   (max 5 MB, must be image/*)
```

**Response:**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/xyz123.jpg",
  "folder": "modules"
}
```

### Upload a course thumbnail

```
POST /api/materials/upload/thumbnail
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <image file>   (max 5 MB, must be image/*)
```

**Response:**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/thumbnails/abc456.jpg",
  "folder": "thumbnails"
}
```

### Upload a video (lecture)

```
POST /api/materials/upload/video
Authorization: Bearer <token>  (admin or teacher only)
Content-Type: multipart/form-data

file: <video file>   (max 500 MB, must be video/*)
```

**Response:**
```json
{
  "success": true,
  "url": "https://res.cloudinary.com/dt6rxrpqr/video/upload/cyberlearnix/lectures/vid789.mp4"
}
```

> **How to tell images apart by URL:**
> - `.../cyberlearnix/thumbnails/...` → course thumbnail  
> - `.../cyberlearnix/modules/...` → chapter or sub-chapter image  
> - `.../cyberlearnix/lectures/...` → video  
> - `.../cyberlearnix/banners/...` → marketing banner  

---

## 3. Chapters (Modules)

### 3.1 Create a chapter

```
POST /api/course-management/courses/{courseId}/modules
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "title": "Introduction to Networking",
  "description": "This chapter covers networking fundamentals.",
  "imageUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/chapter1.jpg",
  "orderIndex": 1
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | string | ✅ | Chapter name |
| `description` | string | ❌ | Short description shown to students |
| `imageUrl` | string | ❌ | Upload first via `/upload/module-image`, paste URL here |
| `orderIndex` | number | ❌ | Position in course (1, 2, 3…). Auto-assigned if not sent |

**Response:**
```json
{
  "success": true,
  "module": {
    "id": 5,
    "title": "Introduction to Networking",
    "description": "This chapter covers networking fundamentals.",
    "imageUrl": "https://res.cloudinary.com/...",
    "orderIndex": 1,
    "isActive": true,
    "createdAt": "2026-04-26T10:00:00"
  }
}
```

> **Save the `id`** — you need it to add sub-chapters and content.

---

### 3.2 Get all chapters of a course (with sub-chapters nested)

```
GET /api/course-management/courses/{courseId}/modules
Authorization: Bearer <token>
```

**Response:**
```json
{
  "success": true,
  "courseId": 1,
  "modules": [
    {
      "id": 5,
      "title": "Introduction to Networking",
      "description": "...",
      "imageUrl": "https://res.cloudinary.com/.../modules/chapter1.jpg",
      "orderIndex": 1,
      "isActive": true,
      "subModules": [
        {
          "id": 8,
          "title": "OSI Model",
          "description": "...",
          "imageUrl": "https://res.cloudinary.com/.../modules/osi.jpg",
          "orderIndex": 1,
          "isActive": true
        }
      ]
    }
  ]
}
```

---

### 3.3 Update a chapter

```
PUT /api/course-management/modules/{moduleId}
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body** (send only fields you want to change):
```json
{
  "title": "Updated Chapter Title",
  "description": "Updated description",
  "imageUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/new.jpg",
  "orderIndex": 2
}
```

**Response:**
```json
{
  "success": true,
  "module": { ...updated module object... }
}
```

---

## 4. Sub-Chapters (Sub-Modules)

Sub-chapters belong to a parent chapter. They can have their own content (videos, text, images).

### 4.1 Create a sub-chapter

```
POST /api/course-management/modules/{parentModuleId}/submodules
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**
```json
{
  "title": "OSI Model Layers",
  "description": "Deep dive into each OSI layer.",
  "imageUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/osi.jpg",
  "orderIndex": 1
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | string | ✅ | Sub-chapter name |
| `description` | string | ❌ | |
| `imageUrl` | string | ❌ | Upload first via `/upload/module-image` |
| `orderIndex` | number | ❌ | Auto-assigned if not sent |

**Response:**
```json
{
  "success": true,
  "subModule": {
    "id": 8,
    "title": "OSI Model Layers",
    "description": "Deep dive into each OSI layer.",
    "imageUrl": "https://res.cloudinary.com/...",
    "orderIndex": 1,
    "isActive": true
  }
}
```

---

### 4.2 Get all sub-chapters of a chapter

```
GET /api/course-management/modules/{moduleId}/submodules
Authorization: Bearer <token>
```

**Response:**
```json
{
  "success": true,
  "parentModuleId": 5,
  "parentTitle": "Introduction to Networking",
  "subModules": [
    {
      "id": 8,
      "title": "OSI Model Layers",
      "imageUrl": "...",
      "orderIndex": 1
    }
  ]
}
```

---

### 4.3 Update a sub-chapter

Same endpoint as updating a chapter — use the sub-chapter's own `id`:

```
PUT /api/course-management/modules/{subModuleId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Updated Sub-Chapter Title",
  "imageUrl": "https://res.cloudinary.com/..."
}
```

---

## 5. Content inside a Chapter/Sub-Chapter

Content goes inside **any module** (chapter or sub-chapter). Use the module's `id` as `{moduleId}`.

### 5.1 Content types overview

| `contentType` | What it stores | Extra fields |
|---|---|---|
| `VIDEO` | A lecture video | `videoUrl`, `durationMinutes`, `isPreview` |
| `LECTURE` | Video + notes | `videoUrl`, `contentText`, `attachmentUrl` |
| `IMAGE` | A single image with caption | `imageUrl`, `caption` |
| `TEXT` | Rich text article | `contentText`, `contentBlocks` (JSON) |
| `QUIZ` | Quiz/test | `quizId`, `timeLimitMinutes`, `passingScore`, `maxAttempts` |
| `EXAM` | Exam (same as quiz) | Same as QUIZ |
| `ASSIGNMENT` | Assignment task | `instructions`, `maxScore` |
| `LAB` | Hands-on lab | `instructions`, `labType`, `environmentConfig` |

---

### 5.2 Create VIDEO content

```
POST /api/course-management/modules/{moduleId}/contents
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "title": "Introduction Video",
  "description": "Watch this before starting the chapter.",
  "contentType": "VIDEO",
  "videoUrl": "https://res.cloudinary.com/dt6rxrpqr/video/upload/cyberlearnix/lectures/vid789.mp4",
  "durationMinutes": 12,
  "isPreview": true,
  "orderIndex": 1
}
```

| Field | Required | Notes |
|---|---|---|
| `title` | ✅ | |
| `contentType` | ✅ | Must be `"VIDEO"` |
| `videoUrl` | ✅ | Upload first via `/upload/video` |
| `durationMinutes` | ❌ | Video length in minutes |
| `isPreview` | ❌ | `true` = students can watch without enrolling |
| `description` | ❌ | |
| `orderIndex` | ❌ | Auto-assigned if not sent |

---

### 5.3 Create IMAGE content

```json
{
  "title": "Network Diagram",
  "contentType": "IMAGE",
  "imageUrl": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/diagram.jpg",
  "caption": "OSI Model illustrated",
  "orderIndex": 2
}
```

| Field | Required | Notes |
|---|---|---|
| `title` | ✅ | |
| `contentType` | ✅ | Must be `"IMAGE"` |
| `imageUrl` | ✅ | Upload first via `/upload/module-image` |
| `caption` | ❌ | Text shown below the image |

---

### 5.4 Create TEXT content (rich article)

```json
{
  "title": "What is the OSI Model?",
  "contentType": "TEXT",
  "contentText": "Plain text fallback for the article.",
  "contentBlocks": "[{\"type\":\"HEADING\",\"level\":1,\"text\":\"What is the OSI Model?\"},{\"type\":\"PARAGRAPH\",\"text\":\"The OSI model is a conceptual framework...\"},{\"type\":\"SUBHEADING\",\"text\":\"Layer 1 - Physical\"},{\"type\":\"PARAGRAPH\",\"text\":\"The physical layer transmits raw bits...\"}]",
  "orderIndex": 3
}
```

#### `contentBlocks` JSON format

`contentBlocks` is a **JSON string** (stringify it before sending). Each block is an object:

```json
[
  { "type": "HEADING",    "level": 1, "text": "Main Title" },
  { "type": "SUBHEADING", "text": "Section Title" },
  { "type": "PARAGRAPH",  "text": "Body text goes here." },
  { "type": "BULLET",     "items": ["Point one", "Point two", "Point three"] },
  { "type": "IMAGE",      "url": "https://res.cloudinary.com/...", "caption": "Optional caption" },
  { "type": "VIDEO",      "url": "https://res.cloudinary.com/..." }
]
```

| Block type | Fields |
|---|---|
| `HEADING` | `level` (1–3), `text` |
| `SUBHEADING` | `text` |
| `PARAGRAPH` | `text` |
| `BULLET` | `items` (array of strings) |
| `IMAGE` | `url`, `caption` (optional) |
| `VIDEO` | `url` |

**JavaScript example:**
```js
const blocks = [
  { type: "HEADING", level: 1, text: "What is the OSI Model?" },
  { type: "PARAGRAPH", text: "The OSI model defines..." },
  { type: "SUBHEADING", text: "Physical Layer" },
  { type: "PARAGRAPH", text: "Handles raw bit transmission." }
];

const body = {
  title: "What is the OSI Model?",
  contentType: "TEXT",
  contentText: "The OSI model defines...",  // plain fallback
  contentBlocks: JSON.stringify(blocks)
};
```

---

### 5.5 Create LECTURE content (video + notes)

```json
{
  "title": "TCP/IP Explained",
  "contentType": "LECTURE",
  "videoUrl": "https://res.cloudinary.com/.../lectures/tcpip.mp4",
  "contentText": "Supplementary reading notes for this lecture.",
  "attachmentUrl": "https://res.cloudinary.com/.../attachments/notes.pdf",
  "durationMinutes": 20,
  "isPreview": false,
  "orderIndex": 4
}
```

---

### 5.6 Create QUIZ / EXAM content

```json
{
  "title": "Chapter 1 Quiz",
  "contentType": "QUIZ",
  "quizId": "quiz-101",
  "timeLimitMinutes": 30,
  "passingScore": 70,
  "maxAttempts": 3,
  "orderIndex": 5
}
```

---

### 5.7 Get all content of a chapter/sub-chapter

```
GET /api/course-management/modules/{moduleId}/contents
Authorization: Bearer <token>
```

**Response:**
```json
{
  "success": true,
  "moduleTitle": "Introduction to Networking",
  "contents": [
    {
      "id": 12,
      "title": "Introduction Video",
      "contentType": "VIDEO",
      "orderIndex": 1,
      "videoUrl": "https://res.cloudinary.com/...",
      "durationMinutes": 12,
      "isPreview": true
    },
    {
      "id": 13,
      "title": "Network Diagram",
      "contentType": "IMAGE",
      "orderIndex": 2,
      "imageUrl": "https://res.cloudinary.com/..."
    }
  ]
}
```

---

### 5.8 Update content

```
PUT /api/course-management/contents/{contentId}
Authorization: Bearer <token>
Content-Type: application/json
```

Send **only the fields you want to change**:

```json
{
  "title": "Updated Title",
  "videoUrl": "https://res.cloudinary.com/...new-video.mp4",
  "durationMinutes": 15
}
```

**Response:**
```json
{
  "success": true,
  "content": { ...updated content object... }
}
```

---

## 6. Delete Endpoints

> Only `admin` role can delete.

### Delete a chapter or sub-chapter
```
DELETE /api/course-management/modules/{moduleId}
Authorization: Bearer <token>
```

### Delete a content item
```
DELETE /api/course-management/contents/{contentId}
Authorization: Bearer <token>
```

**Response:**
```json
{ "success": true, "message": "Module deleted successfully" }
```

---

## 7. Full Course Tree (read everything at once)

Gets the course details + all modules + all content in one call:

```
GET /api/course-management/courses/{courseId}/full
Authorization: Bearer <token>
```

---

## 8. Typical UI Workflow

### Admin creates a full course structure

```
Step 1  Create course
        POST /api/course-management/courses
        → get courseId (e.g. 1)

Step 2  Upload chapter image (optional)
        POST /api/materials/upload/module-image
        → get imageUrl

Step 3  Create chapter
        POST /api/course-management/courses/1/modules
        body: { title, description, imageUrl }
        → get moduleId (e.g. 5)

Step 4  Upload sub-chapter image (optional)
        POST /api/materials/upload/module-image
        → get imageUrl

Step 5  Create sub-chapter under chapter
        POST /api/course-management/modules/5/submodules
        body: { title, description, imageUrl }
        → get subModuleId (e.g. 8)

Step 6  Upload video
        POST /api/materials/upload/video
        → get videoUrl

Step 7  Add video content to sub-chapter
        POST /api/course-management/modules/8/contents
        body: { title, contentType: "VIDEO", videoUrl, durationMinutes }

Step 8  Add text article to sub-chapter
        POST /api/course-management/modules/8/contents
        body: { title, contentType: "TEXT", contentText, contentBlocks }

Step 9  Verify the full structure
        GET /api/course-management/courses/1/modules
```

---

## 9. Response Shapes Reference

### Module object
```json
{
  "id": 5,
  "title": "Introduction to Networking",
  "description": "...",
  "imageUrl": "https://res.cloudinary.com/.../modules/chapter1.jpg",
  "orderIndex": 1,
  "isActive": true,
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-26T10:00:00",
  "updatedAt": "2026-04-26T10:00:00"
}
```

### Content object (VIDEO)
```json
{
  "id": 12,
  "title": "Introduction Video",
  "description": "...",
  "contentType": "VIDEO",
  "orderIndex": 1,
  "videoUrl": "https://res.cloudinary.com/.../lectures/vid789.mp4",
  "durationMinutes": 12,
  "isPreview": true,
  "createdAt": "2026-04-26T10:05:00"
}
```

### Content object (IMAGE)
```json
{
  "id": 13,
  "title": "Network Diagram",
  "contentType": "IMAGE",
  "orderIndex": 2,
  "imageUrl": "https://res.cloudinary.com/.../modules/diagram.jpg",
  "contentText": "OSI Model illustrated"
}
```

### Content object (TEXT)
```json
{
  "id": 14,
  "title": "What is the OSI Model?",
  "contentType": "TEXT",
  "orderIndex": 3,
  "contentText": "Plain text fallback...",
  "contentBlocks": "[{\"type\":\"HEADING\",\"level\":1,\"text\":\"What is the OSI Model?\"}]"
}
```

### Error response
```json
{
  "error": "You are not assigned to this course."
}
```

---

## Quick Reference — All Endpoints

| Method | URL | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/auth/login` | Login, get token | any |
| `POST` | `/api/materials/upload/module-image` | Upload chapter/sub-chapter image | any auth |
| `POST` | `/api/materials/upload/thumbnail` | Upload course thumbnail | any auth |
| `POST` | `/api/materials/upload/video` | Upload lecture video | admin/teacher |
| `POST` | `/api/course-management/courses/{id}/modules` | Create chapter | admin/teacher |
| `GET` | `/api/course-management/courses/{id}/modules` | List chapters + nested sub-chapters | admin/teacher |
| `PUT` | `/api/course-management/modules/{id}` | Update chapter | admin/teacher |
| `DELETE` | `/api/course-management/modules/{id}` | Delete chapter | admin |
| `POST` | `/api/course-management/modules/{id}/submodules` | Create sub-chapter | admin/teacher |
| `GET` | `/api/course-management/modules/{id}/submodules` | List sub-chapters | admin/teacher |
| `POST` | `/api/course-management/modules/{id}/contents` | Add content | admin/teacher |
| `GET` | `/api/course-management/modules/{id}/contents` | List content | admin/teacher |
| `PUT` | `/api/course-management/contents/{id}` | Update content | admin/teacher |
| `DELETE` | `/api/course-management/contents/{id}` | Delete content | admin |
| `GET` | `/api/course-management/courses/{id}/full` | Full course tree | admin/teacher |
