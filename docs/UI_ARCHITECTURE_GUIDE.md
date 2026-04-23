# Cyberlearnix — UI Architecture & Frontend Integration Guide

> **For: Frontend / UI Developer**
> This document explains every backend service, every API endpoint the UI needs to call, the three role-based dashboards, and a recommended React folder structure. Read top to bottom before writing any UI code.

---

## 1. System Overview

```
Browser / Mobile App
        │
        ▼
  ┌─────────────────────────────────┐
  │   API Gateway  :8080            │  ← Single entry point for ALL API calls
  │   (Spring Cloud Gateway)        │    Handles JWT validation + routing
  └──────────┬──────────────────────┘
             │  routes by path prefix
    ┌─────────┴──────────────────────────────────────────────────┐
    │                                                            │
    ▼                                                            ▼
user-service:8081          course-service:8082
admin-service:8090         enrollment-service:8083
instructor-service:8091    notification-service:8084
cms-service:8089           shop-service:8085
                           form-service:8087
```

**The UI NEVER talks to individual services directly.**
**Every request goes to `http://localhost:8080` (the gateway).**

---

## 2. Authentication Flow

### 2.1 Login

```
POST /api/auth/login
Body: { "email": "user@email.com", "password": "..." }

Response (200):
{
  "token": "eyJhbGci...",          ← ACCESS TOKEN  (store in memory / localStorage)
  "tokenType": "Bearer",
  "expiresIn": 3600000,            ← 1 hour in ms
  "user": {
    "id": "uuid",
    "email": "...",
    "name": "...",
    "role": "admin | teacher | student | dual"
  }
}
+ httpOnly Cookie: refresh_token   ← browser stores automatically, DO NOT read it
```

### 2.2 Refresh Token (call when access token is near expiry)

```
POST /api/auth/refresh-token
No body needed — cookie sent automatically by browser

Response (200):
{
  "token": "eyJhbGci...",          ← NEW access token
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

### 2.3 Logout

```
POST /api/auth/logout
Header: Authorization: Bearer <token>

Response (200): { "message": "Logged out successfully" }
```

### 2.4 OTP Login (alternative)

```
POST /api/auth/request-otp    Body: { "email": "..." }
POST /api/auth/verify-otp-login  Body: { "email": "...", "otp": "123456" }
```

### 2.5 How to attach token to every request

```js
// In your API utility / Axios interceptor
headers: {
  Authorization: `Bearer ${accessToken}`
}
```

### 2.6 Role → Dashboard Redirect Map

| Role | Redirect after login |
|------|---------------------|
| `admin` | `/admin/dashboard` |
| `teacher` | `/instructor/dashboard` |
| `dual` | `/instructor/dashboard` |
| `student` | `/student/dashboard` |

---

## 3. Services & API Reference

> **Base URL for all calls: `http://localhost:8080`**

---

### 3.1 Auth APIs — `/api/auth/**` → user-service:8081

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| POST | `/api/auth/login` | Public | Login with email + password |
| POST | `/api/auth/register` | Public | Register new account |
| POST | `/api/auth/logout` | Authenticated | Invalidate token |
| POST | `/api/auth/refresh-token` | Authenticated | Get new access token |
| POST | `/api/auth/request-otp` | Public | Send OTP to email |
| POST | `/api/auth/verify-otp` | Public | Verify OTP (2FA) |
| POST | `/api/auth/verify-otp-login` | Public | Login via OTP |

---

### 3.2 User APIs — `/api/users/**` → user-service:8081

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/users/all` | Admin | List all users |
| GET | `/api/users/{id}/profile` | Authenticated | Get user profile |
| PUT | `/api/users/{id}` | Admin / Self | Update user |
| PUT | `/api/users/{id}/status` | Admin | Enable/disable account |
| DELETE | `/api/users/{id}` | Admin | Delete user |
| GET | `/api/users/{id}/teacher-permission` | Admin | Get teacher permissions |
| PUT | `/api/users/{id}/teacher-permission` | Admin | Set teacher permissions |

---

### 3.3 Course APIs — `/api/courses/**` → course-service:8082

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/courses` | Public | Browse all courses |
| GET | `/api/courses?id={id}` | Public | Single course detail |
| POST | `/api/courses` | Admin | Create new master course |
| PUT | `/api/courses/{id}` | Admin | Update course |
| DELETE | `/api/courses/{id}` | Admin | Delete course |
| GET | `/api/courses/{id}/curriculum` | Public | Course modules + content |
| PUT | `/api/courses/{id}/status` | Admin | Approve / reject course |
| GET | `/api/courses/teachers` | Admin/Teacher | Course-teacher assignments |
| POST | `/api/courses/teachers` | Admin | Assign teacher to course |
| DELETE | `/api/courses/teachers` | Admin | Remove teacher from course |

#### Course Progress (student)
| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/courses/progress/{studentId}` | Student/Admin | Get progress |
| POST | `/api/courses/progress` | Student | Update progress |

#### Course Management (admin)
| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/course-management/content` | Admin | List all content |
| POST | `/api/course-management/content` | Admin | Add content to module |
| PUT | `/api/course-management/content/{id}` | Admin | Edit content |
| DELETE | `/api/course-management/content/{id}` | Admin | Delete content |

#### Materials Upload
| Method | Path | Who | Description |
|--------|------|-----|-------------|
| POST | `/api/materials/upload/{courseId}` | Admin/Teacher | Upload file attachment |

---

### 3.4 Enrollment APIs — `/api/enrollments/**` → enrollment-service:8083

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/enrollments` | Student/Admin | Get enrollments |
| POST | `/api/enrollments` | Student | Enroll in course |
| GET | `/api/enrollments?studentId=&courseId=` | Student | Single enrollment status |
| GET | `/api/enrollments/course/{courseId}/students` | Teacher/Admin | Students in a course |
| GET | `/api/enrollments/student/{id}/progress` | Student/Teacher | Progress detail |
| GET | `/api/enrollments/stats` | Admin | Enrollment statistics |

#### Payments
| Method | Path | Who | Description |
|--------|------|-----|-------------|
| POST | `/api/payments` | Student | Initiate payment |
| GET | `/api/payments/{id}` | Student/Admin | Payment status |
| POST | `/api/payu-payment` | Student | PayU payment gateway |

#### Forms
| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/enrollments/config?formId=` | Public | Get enrollment form config |
| POST | `/api/forms/{id}/responses` | Public | Submit form response |

---

### 3.5 Admin APIs — `/api/admin/**` → admin-service:8090

> All admin APIs require `role: admin` in JWT.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/auth/login` | Admin-specific login (validates role) |
| POST | `/api/admin/auth/logout` | Admin logout |
| GET | `/api/admin/auth/profile` | Admin own profile |
| GET | `/api/admin/reports/users` | User statistics |
| GET | `/api/admin/reports/courses` | Course statistics |
| GET | `/api/admin/reports/revenue` | Revenue statistics |
| GET | `/api/admin/users` | All users list |
| PUT | `/api/admin/users/{id}/status` | Block/unblock user |
| GET | `/api/admin/courses` | All courses with moderation status |
| PUT | `/api/admin/courses/{id}/status` | Approve/reject course |
| GET | `/api/admin/orders` | All payment/order records |
| GET | `/api/admin/settings` | Platform settings |
| PUT | `/api/admin/settings` | Update platform settings |

---

### 3.6 Instructor APIs — `/api/instructor/**` → instructor-service:8091

> Requires `role: teacher | dual | admin`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/instructor/dashboard` | Summary stats (courses, students, ratings) |
| GET | `/api/instructor/courses` | Instructor's courses |
| GET | `/api/instructor/courses/{id}` | Single course detail |
| GET | `/api/instructor/courses/{id}/content` | Course curriculum |
| POST | `/api/instructor/courses/{id}/supplemental-content` | Add extra content |
| GET | `/api/instructor/students` | All students across instructor's courses |
| GET | `/api/instructor/students/course/{id}` | Students in a specific course |
| GET | `/api/instructor/students/{studentId}/progress` | Student progress |
| GET | `/api/instructor/profile` | Instructor's own profile |
| PATCH | `/api/instructor/profile` | Update own profile |
| GET | `/api/instructor/feedback` | Ratings + feedback received |
| GET | `/api/instructor/analytics` | Course + enrollment analytics |

---

### 3.7 CMS APIs — `/api/cms/**` → cms-service:8089

| Method | Path | Who | Description |
|--------|------|-----|-------------|
| GET | `/api/pages` | Public | List all CMS pages |
| GET | `/api/pages/{slug}` | Public | Single page by slug |
| POST | `/api/cms/pages` | Admin | Create CMS page |
| PUT | `/api/cms/pages/{id}` | Admin | Update CMS page |
| DELETE | `/api/cms/pages/{id}` | Admin | Delete CMS page |
| POST | `/api/cms/media` | Admin | Upload media |
| GET | `/api/cms/media` | Admin | List uploaded media |

---

### 3.8 Content / Marketing APIs — course-service:8082

| Group | Method | Path | Description |
|-------|--------|------|-------------|
| Banners | GET | `/api/banners` | Homepage banners |
| Banners | POST/PUT/DELETE | `/api/banners/**` | Manage banners (Admin) |
| Promos | GET | `/api/promos` | Active promo banners |
| Updates | GET | `/api/updates` | Platform updates feed |
| Partners | GET | `/api/partners` | Partner logos/links |
| Suggestions | GET | `/api/suggestions` | Course recommendations |
| Content Reviews | GET | `/api/content-reviews` | Pending reviews (Admin) |

---

### 3.9 Communication APIs — user-service:8081

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/teams` | Team members list |
| GET | `/api/careers` | Job openings |
| POST | `/api/careers` | Post a job (Admin) |
| GET | `/api/contact-submissions` | Contact form submissions |
| POST | `/api/contact-submissions` | Submit contact form (Public) |
| GET | `/api/site-settings` | Public site settings |
| GET | `/api/menus` | Navigation menu config |
| POST | `/api/chatbot` | Chatbot message |
| GET | `/api/activity` | Activity log (Admin) |
| POST | `/api/send-email` | Send email (Admin) |

---

### 3.10 Notifications — `/api/notifications/**` → notification-service:8084

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/notifications` | User notifications |
| PATCH | `/api/notifications/{id}/read` | Mark as read |
| DELETE | `/api/notifications/{id}` | Delete notification |

---

## 4. Role-Based UI Sections

### 4.1 Admin Role (`role === "admin"`)

Pages the admin can access:

```
/admin/dashboard          → GET /api/admin/reports/* (user stats, course stats, revenue)
/admin/users              → GET /api/admin/users  |  PUT /api/admin/users/{id}/status
/admin/courses            → GET /api/admin/courses  |  PUT /api/admin/courses/{id}/status
/admin/courses/create     → POST /api/courses
/admin/courses/{id}/edit  → PUT /api/courses/{id}
/admin/enrollments        → GET /api/enrollments (admin sees all)
/admin/payments           → GET /api/admin/payments
/admin/reports            → GET /api/admin/reports/**
/admin/settings           → GET/PUT /api/admin/settings
/admin/cms                → GET/POST/PUT /api/cms/pages
/admin/banners            → GET/POST/PUT /api/banners
/admin/notifications      → GET /api/notifications
```

---

### 4.2 Instructor / Teacher Role (`role === "teacher" | "dual"`)

```
/instructor/dashboard          → GET /api/instructor/dashboard
/instructor/courses            → GET /api/instructor/courses
/instructor/courses/{id}       → GET /api/instructor/courses/{id}
/instructor/courses/{id}/students → GET /api/instructor/students/course/{id}
/instructor/courses/{id}/content  → GET /api/instructor/courses/{id}/content
/instructor/courses/{id}/add-content → POST /api/instructor/courses/{id}/supplemental-content
/instructor/students           → GET /api/instructor/students
/instructor/students/{id}      → GET /api/instructor/students/{id}/progress
/instructor/feedback           → GET /api/instructor/feedback
/instructor/analytics          → GET /api/instructor/analytics
/instructor/profile            → GET/PATCH /api/instructor/profile
```

---

### 4.3 Student Role (`role === "student"`)

```
/student/dashboard        → GET /api/enrollments?studentId={id}  +  course data
/student/courses          → GET /api/courses (browse all)
/student/courses/{id}     → GET /api/courses?id={id}  (course detail)
/student/my-courses       → GET /api/enrollments?studentId={id}
/student/progress/{id}    → GET /api/enrollments/student/{id}/progress
/student/certificates     → GET /api/courses/certificates/{studentId}
/student/notifications    → GET /api/notifications
/student/profile          → GET /api/users/{id}/profile
```

---

### 4.4 Public (no login required)

```
/                         → GET /api/banners  +  /api/courses  +  /api/partners
/courses                  → GET /api/courses
/courses/{id}             → GET /api/courses?id={id}  +  /api/courses/{id}/curriculum
/about                    → GET /api/pages/about  (CMS)
/contact                  → POST /api/contact-submissions
/careers                  → GET /api/careers
/blog                     → GET /api/pages?category=blog  (CMS)
/login                    → POST /api/auth/login
/register                 → POST /api/auth/register
```

---

## 5. JWT — What The UI Needs To Know

```
Token payload structure (decoded):
{
  "sub": "user-uuid",         ← user ID
  "role": "admin",            ← ROLE (admin | teacher | student | dual)
  "type": "access",
  "iat": 1713000000,
  "exp": 1713003600
}
```

**Rules:**
- Store access token in memory (React Context / Zustand / Redux) or `localStorage`
- DO NOT store refresh token — it lives in an httpOnly cookie managed by the browser
- Call `POST /api/auth/refresh-token` when the access token expires (check `exp` field)
- On 401 response: try refresh once → if that fails, redirect to `/login`
- Every protected API call must include: `Authorization: Bearer <access_token>`

---

## 6. Recommended Frontend Folder Structure

```
src/
│
├── api/                          ← All API functions grouped by service
│   ├── authApi.js                ← login, logout, refresh, register, otp
│   ├── userApi.js                ← getUsers, updateUser, updateStatus
│   ├── courseApi.js              ← getCourses, getCourse, createCourse, progress
│   ├── enrollmentApi.js          ← enroll, getEnrollments, getProgress
│   ├── adminApi.js               ← reports, settings, moderation
│   ├── instructorApi.js          ← dashboard, students, feedback, analytics
│   ├── cmsApi.js                 ← pages, media
│   ├── notificationApi.js        ← getNotifications, markRead
│   └── axiosInstance.js          ← base Axios config + interceptors (attach Bearer token)
│
├── components/                   ← Reusable UI components
│   ├── common/
│   │   ├── Navbar.jsx
│   │   ├── Sidebar.jsx
│   │   ├── Footer.jsx
│   │   ├── LoadingSpinner.jsx
│   │   ├── ProtectedRoute.jsx    ← checks token + role
│   │   ├── RoleGuard.jsx         ← renders children only if role matches
│   │   └── NotificationBell.jsx
│   ├── course/
│   │   ├── CourseCard.jsx
│   │   ├── CourseList.jsx
│   │   ├── CourseDetail.jsx
│   │   └── CurriculumAccordion.jsx
│   ├── enrollment/
│   │   ├── EnrollButton.jsx
│   │   └── ProgressBar.jsx
│   └── admin/
│       ├── StatsCard.jsx
│       ├── UserTable.jsx
│       └── CourseTable.jsx
│
├── pages/                        ← One folder per role + public
│   │
│   ├── public/                   ← No login required
│   │   ├── Home.jsx
│   │   ├── CourseBrowse.jsx
│   │   ├── CourseDetailPage.jsx
│   │   ├── Login.jsx
│   │   ├── Register.jsx
│   │   ├── Contact.jsx
│   │   ├── Careers.jsx
│   │   └── CmsPage.jsx           ← renders any CMS page by slug
│   │
│   ├── admin/                    ← Role: admin
│   │   ├── Dashboard.jsx         ← /admin/dashboard
│   │   ├── Users.jsx             ← /admin/users
│   │   ├── Courses.jsx           ← /admin/courses
│   │   ├── CreateCourse.jsx      ← /admin/courses/create
│   │   ├── EditCourse.jsx        ← /admin/courses/:id/edit
│   │   ├── Enrollments.jsx       ← /admin/enrollments
│   │   ├── Payments.jsx          ← /admin/payments
│   │   ├── Reports.jsx           ← /admin/reports
│   │   ├── Settings.jsx          ← /admin/settings
│   │   ├── CmsManager.jsx        ← /admin/cms
│   │   └── Banners.jsx           ← /admin/banners
│   │
│   ├── instructor/               ← Role: teacher | dual
│   │   ├── Dashboard.jsx         ← /instructor/dashboard
│   │   ├── MyCourses.jsx         ← /instructor/courses
│   │   ├── CourseDetail.jsx      ← /instructor/courses/:id
│   │   ├── CourseStudents.jsx    ← /instructor/courses/:id/students
│   │   ├── AddContent.jsx        ← /instructor/courses/:id/add-content
│   │   ├── Students.jsx          ← /instructor/students
│   │   ├── StudentProgress.jsx   ← /instructor/students/:id
│   │   ├── Feedback.jsx          ← /instructor/feedback
│   │   ├── Analytics.jsx         ← /instructor/analytics
│   │   └── Profile.jsx           ← /instructor/profile
│   │
│   └── student/                  ← Role: student
│       ├── Dashboard.jsx         ← /student/dashboard
│       ├── BrowseCourses.jsx     ← /student/courses
│       ├── MyCourses.jsx         ← /student/my-courses
│       ├── CoursePlayer.jsx      ← /student/courses/:id/learn
│       ├── Progress.jsx          ← /student/progress/:id
│       ├── Certificates.jsx      ← /student/certificates
│       ├── Notifications.jsx     ← /student/notifications
│       └── Profile.jsx           ← /student/profile
│
├── store/                        ← State management (Zustand / Redux)
│   ├── authStore.js              ← { user, token, role, login, logout, refreshToken }
│   ├── courseStore.js
│   ├── enrollmentStore.js
│   └── notificationStore.js
│
├── hooks/                        ← Custom React hooks
│   ├── useAuth.js                ← read authStore, auto-refresh logic
│   ├── useCourses.js
│   ├── useEnrollment.js
│   └── useRole.js                ← returns current role, isAdmin(), isInstructor(), etc.
│
├── router/
│   └── AppRouter.jsx             ← React Router config with role-based guards
│       // Example:
│       // <Route path="/admin/*" element={<RoleGuard role="admin"><AdminLayout /></RoleGuard>} />
│       // <Route path="/instructor/*" element={<RoleGuard role={["teacher","dual"]}><InstructorLayout /></RoleGuard>} />
│       // <Route path="/student/*" element={<RoleGuard role="student"><StudentLayout /></RoleGuard>} />
│
├── layouts/
│   ├── PublicLayout.jsx
│   ├── AdminLayout.jsx           ← Admin sidebar + top bar
│   ├── InstructorLayout.jsx      ← Instructor sidebar + top bar
│   └── StudentLayout.jsx         ← Student sidebar + top bar
│
└── utils/
    ├── tokenUtils.js             ← decodeToken(), isExpired(), getRoleFromToken()
    ├── dateUtils.js
    └── formatUtils.js
```

---

## 7. Axios Instance Setup (Critical)

```js
// src/api/axiosInstance.js
import axios from 'axios';

const BASE_URL = 'http://localhost:8080';  // Gateway — NEVER change individual ports

const api = axios.create({ baseURL: BASE_URL, withCredentials: true }); // withCredentials → sends httpOnly cookie for refresh

// Attach token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken'); // or from memory store
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Auto-refresh on 401
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
      error.config._retry = true;
      try {
        const { data } = await axios.post(`${BASE_URL}/api/auth/refresh-token`, {}, { withCredentials: true });
        localStorage.setItem('accessToken', data.token);
        error.config.headers.Authorization = `Bearer ${data.token}`;
        return api(error.config); // retry original request
      } catch {
        localStorage.removeItem('accessToken');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
```

---

## 8. Gateway Headers Passed to Services

The gateway injects these headers from the JWT automatically — UI does not need to send them manually:

| Header | Value | Used by |
|--------|-------|---------|
| `X-User-Id` | UUID of logged-in user | course-service, enrollment-service |
| `X-User-Role` | `admin \| teacher \| student \| dual` | All services |
| `Authorization` | `Bearer <token>` | All services |

---

## 9. Service Port Quick Reference

| Service | Port | API Prefix |
|---------|------|------------|
| **API Gateway** | **8080** | **All requests go here** |
| user-service | 8081 | `/api/auth/**`, `/api/users/**` |
| course-service | 8082 | `/api/courses/**`, `/api/banners/**`, `/api/promos/**` |
| enrollment-service | 8083 | `/api/enrollments/**`, `/api/payments/**` |
| notification-service | 8084 | `/api/notifications/**` |
| shop-service | 8085 | `/api/shop/**` |
| form-service | 8087 | `/api/forms/**` |
| cms-service | 8089 | `/api/cms/**`, `/api/pages/**` |
| admin-service | 8090 | `/api/admin/**` |
| instructor-service | 8091 | `/api/instructor/**` |

---

## 10. Common Response Formats

### Success
```json
{ "success": true, "data": { ... } }
```
or direct object:
```json
{ "id": 1, "title": "...", "status": "PUBLISHED" }
```

### Error
```json
{ "error": "Error message here" }
```
```json
{ "message": "Validation failed", "details": ["field is required"] }
```

### Paginated List
```json
{
  "success": true,
  "courses": [ { ... }, { ... } ],
  "total": 50,
  "page": 1,
  "pageSize": 20
}
```

---

## 11. Environment Variables for UI

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

In production, replace `localhost:8080` with the deployed gateway domain.

---

## 12. WebSocket (Real-time)

The course-service exposes a WebSocket for live course updates:

```
ws://localhost:8080/ws/courses
```

Subscribe to receive real-time notifications of course status changes. Connect with the same JWT token:

```js
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
  stompClient.subscribe('/topic/courses', (msg) => {
    const update = JSON.parse(msg.body);
    // refresh course list
  });
});
```

---

*Last updated: April 2026 | Backend: Spring Boot 3.2.2 | Java 21*
