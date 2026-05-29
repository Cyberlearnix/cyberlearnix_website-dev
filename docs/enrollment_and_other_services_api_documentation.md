# Cyberlearnix Other Services API Documentation

This document covers the Enrollment, Shop, and Administrative services.

---

## 1. Enrollment Service (`/api/enrollments`)

Handles student registration for courses and form submissions.

### 1.1 Enroll in Course
- **POST** `/api/enrollments`
- **Request Body**: `{"courseId": 1, "userId": "UUID"}`

### 1.2 Get My Enrollments
- **GET** `/api/enrollments/my` (Requires Auth Header)

### 1.3 Enrollment Forms (`/api/enrollments/forms`)
- **GET** `/api/enrollments/forms`: List available registration forms.
- **POST** `/api/enrollments/forms/submit`: Submit a specific form.

---

## 2. Payment Service (`/api/payments`)

### 2.1 Initiate Payment
- **POST** `/api/payments/payu-payment`
- **Description**: Generates the PayU hash or redirect URL.
- **Response**: `{"redirectUrl": "...", "txnid": "..."}`

---

## 3. Shop Service (`/api/shop`)

### 3.1 Get Merchandise
- **GET** `/api/shop`: List t-shirts, hoodies, and kits.

---

## 4. Admin Service (`/api/admin`)

Used for platform-wide metrics and configuration.

### 4.1 Stats Dashboard
- **GET** `/api/admin/dashboard/stats`: (Admin Only) Returns daily revenue, new users, and enrollment counts.

---

## 5. Form Service (`/api/forms`)

### 5.1 Generic Forms
- **GET** `/api/forms`: List all site forms.
- **POST** `/api/forms/{id}/responses`: Submit response to a form.

---

## 6. CMS Service (`/api/cms` / `/api/pages`)

### 6.1 Get Dynamic Page
- **GET** `/api/pages/{slug}`: Fetches standard or dynamic pages created in the CMS.
- **Response**: Includes `title`, `content`, `metadata`.
