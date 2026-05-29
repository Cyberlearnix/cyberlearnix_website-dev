# Cyberlearnix: User-Service Frontend Integration Guide

This guide describes the logical flows for integrating the `user-service` with the Cyberlearnix frontend.

---

## 1. Authentication Flows

### 1.1 Passwordless (OTP) Login
Use this flow for a friction-less login experience.

1.  **Initiate**: Call `POST /api/auth/request-login-otp` with `{"email": "..."}`.
    -   *Logic*: Backend verifies user existence and rate-limits requests.
    -   *Success*: Backend sends 6-digit code via email and returns a `sessionId`.
2.  **Verification**: Frontend displays an OTP input field. Call `POST /api/auth/verify-otp-login` with email, OTP, and `sessionId`.
    -   *Success*: Backend returns JWT `token`, `refreshToken`, and `user` object.
    -   *State Management*: Save the `token` in your Auth Store (Pinia/Redux) and `refreshToken` in storage.

### 1.2 Standard Login (Email/Password)
1.  Frontend captures email/password.
2.  Call `POST /api/auth/login`.
    -   *Logic*: Standard Bcrypt comparison.
3.  On success, handle tokens same as Step 1.2 above.

---

## 2. Security & Account Recovery

### 2.1 Password Reset
1.  User clicks "Forgot Password".
2.  Call `POST /api/auth/request-otp` (Request OTP for reset).
3.  Verify OTP: `POST /api/auth/verify-otp`. Returns a **Reset Token**.
4.  Finalize Change: Call `POST /api/auth/reset-password` with the Reset Token and the `newPassword`.

### 2.2 Token Refresh
Setup an axios interceptor (or similar) to handle expired tokens:
1.  When an API returns `401 Unauthorized`.
2.  Call `POST /api/auth/refresh-token` with the stored `refreshToken`.
3.  Update the `token` in your local storage and retry the original failed request.

---

## 3. Public Interactive Flows

### 3.1 Contact Form & Inquiry
1.  User fills "Contact Us" form.
2.  Call `POST /api/contact-submissions`.
    -   *Logic*: Saves the message to the DB and triggers an async email notification to the company admin.
    -   *Feedback*: Show "Message Sent" success toast.

### 3.2 Chatbot Interactions
1.  Frontend sends text or selects a category (e.g. "pricing").
2.  Call `GET /api/chatbot?category=pricing`.
3.  Display the `response` and any `followupQuestions` returned by the API.

---

## 4. Admin Management Flows

#### 4.1 Admin User Creation (New)
1.  Admin logs in and obtains their **Admin Bearer Token**.
2.  Call `POST /api/auth/register`.
    -   *Logic*: Only allowed for users with `ROLE_ADMIN`.
    -   *Request Body*: 
        ```json
        {
          "email": "newuser@example.com",
          "password": "InitialPassword123!",
          "role": "student"
        }
        ```
    -   *Success*: Account is created with the specified initial role.

#### 4.2 Managing Site Settings
1.  Fetch current config: `GET /api/site-settings`.
2.  Update a key: `POST /api/site-settings` with the new data.
    -   *Important*: Ensure the header `X-User-Role: admin` is included along with the Auth Bearer token.

### 4.2 Replying to Users
1.  Admin views submissions (`GET /api/contact-submissions`).
2.  Admin types a reply.
3.  Call `POST /api/send-reply`.
    -   *Logic*: This API builds a professional email template from the admin's text and sends it directly to the user's inbox.
