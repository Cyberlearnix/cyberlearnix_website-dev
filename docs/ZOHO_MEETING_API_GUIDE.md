# Zoho Meeting API Guide — Admin Service

**Service:** admin-service  
**Base URL:** `http://localhost:8090`  
**API Prefix:** `/api/admin/teams/meetings`  
**Total Endpoints:** 5  
**Access:** Admin role only  

---

## Overview

This service integrates with **Zoho Meeting** (Professional plan) to schedule and manage online meetings for students, batches, or teams.

When a meeting is created via the API:
1. The backend calls **Zoho Meeting REST API v2** to create a session
2. Zoho returns a **joinUrl** (participants click this — no Zoho account needed)
3. Meeting details are stored in the local database
4. Admin gets back the full meeting object including the `joinUrl`

> **Timezone:** All meetings are scheduled in **Asia/Kolkata (IST)**.  
> **Date format:** Send all dates as ISO 8601 — `"2026-04-17T10:00:00"`

---

## Zoho Professional Plan — Features

> **This platform runs on Zoho Meeting's Professional plan.**

### Plan Limits

| Limit | Professional Plan |
|-------|------------------|
| **Max meeting duration** | **Up to 24 hours per session** |
| Max attendees per meeting | 250 |
| Number of meetings | Unlimited |
| Session recording | ✅ Available (5 GB cloud storage/host) |
| Recording transcripts | ✅ Available |
| Co-hosts | Up to 5 scheduled; unlimited added during meeting |

### Professional Plan — What's Included

#### Scheduling & Joining
| Feature | Included |
|---------|----------|
| Schedule meetings in advance | ✅ |
| Instant meetings (start now) | ✅ |
| Join by browser (no download required) | ✅ |
| Email invitations to participants | ✅ |
| RSVP (Yes / Maybe / No confirmations) | ✅ |
| Add to calendar (Google, Outlook, iCal) | ✅ |
| Reminder emails before meeting | ✅ |
| Private token-protected meeting links | ✅ |
| Personal room (persistent meeting URL) | ✅ |
| Embed meeting widget | ✅ |
| Join before host | ✅ |

#### In-Session Collaboration
| Feature | Included |
|---------|----------|
| Video conferencing (webcam) | ✅ |
| Audio conferencing (VoIP) | ✅ |
| Screen sharing (full screen / app / tab) | ✅ |
| In-session chat & messaging | ✅ |
| Whiteboard (collaborative) | ✅ |
| Emoji reactions | ✅ |
| Raise hand | ✅ |
| Virtual backgrounds | ✅ |
| Custom virtual backgrounds | ✅ |
| International dial-in numbers | ✅ |
| Meeting polls | ✅ |
| Breakout rooms | ✅ |
| Co-host support (up to 5) | ✅ |
| Remote control (control participant's screen) | ✅ |
| Share materials | ✅ |
| File management (share files in-session) | ✅ |
| Annotation | ✅ |
| Keyboard and mouse sharing | ✅ |

#### Moderator Controls
| Feature | Included |
|---------|----------|
| Mute / unmute participants | ✅ |
| Remove participants | ✅ |
| Advanced moderator settings | ✅ |
| Multiple email reminders | ✅ |
| Lock meeting (prevent new joins) | ✅ |

#### Recording & Analytics
| Feature | Included |
|---------|----------|
| Session recording | ✅ |
| Cloud recording storage (5 GB/host) | ✅ |
| View & share recordings | ✅ |
| Recording transcripts | ✅ |
| Meeting keynotes | ✅ |
| Basic reports (post-session analytics) | ✅ |
| Advanced reports | ✅ |

#### Security
| Feature | Included |
|---------|----------|
| TLS 1.2 / AES-256 encryption | ✅ |
| Two-factor authentication (2FA) | ✅ |
| Consent for turning on video | ✅ |
| GDPR compliance | ✅ |
| End-to-end encryption (E2EE) | ✅ |
| Action log viewer | ✅ |

#### Organisation Management
| Feature | Included |
|---------|----------|
| User management | ✅ |
| Departments | ✅ |
| Co-branding (logo in invitation emails) | ✅ |
| Custom domain | ✅ |
| Multiple portals | ✅ |
| Configure sender/reply-to email address | ✅ |

#### Apps & Extensions
| Feature | Included |
|---------|----------|
| Desktop app (Windows / Mac / Linux) | ✅ |
| iOS mobile app | ✅ |
| Android mobile app | ✅ |
| Chrome extension | ✅ |
| Firefox extension | ✅ |
| Outlook plugin | ✅ |
| Zoho CRM integration | ✅ |
| Zoho Calendar integration | ✅ |
| Zia AI integration | ✅ |
| Rev AI (transcription) integration | ✅ |

---

## Required Headers (All Endpoints)

| Header | Value | Description |
|--------|-------|-------------|
| `X-User-Role` | `admin` | Must be `admin` — enforced by security |
| `X-User-Id` | Your admin UUID | Your logged-in admin user ID |
| `Authorization` | `Bearer <jwt_token>` | JWT from login (gateway adds this automatically in prod) |
| `Content-Type` | `application/json` | Required for POST/PUT requests |

---

## Meeting Status Values

| Status | Meaning |
|--------|---------|
| `SCHEDULED` | Meeting is active and joinUrl is usable |
| `CANCELLED` | Meeting was cancelled — joinUrl is no longer valid |

---

## 1. Create Meeting

`POST /api/admin/teams/meetings`

Schedules a new meeting in Zoho and stores it locally. Returns the live `joinUrl` to share with students.

### Request Body

| Field | Type | Required | Validation | Description |
|-------|------|----------|-----------|-------------|
| `subject` | string | **Yes** | Cannot be blank | Meeting title/topic |
| `startDateTime` | string (ISO 8601) | **Yes** | Must be in the future | When the meeting starts |
| `endDateTime` | string (ISO 8601) | **Yes** | Must be after startDateTime | When the meeting ends (up to 24 hours after startDateTime) |
| `description` | string | No | Max 2000 chars | Agenda or meeting notes shown to participants |
| `invitees` | array of objects | No | Each `email` must be valid | People to invite — Zoho emails each one a personalised join link |
| `recurring` | boolean | No | Default: `false` | Set to `true` to create a recurring meeting |
| `repeatType` | string | Required if recurring | `DAILY`, `WEEKLY`, `MONTHLY` | How often the meeting repeats |
| `repeatEvery` | number | No | Min: 1, Default: 1 | Interval — e.g. `2` means every 2 days/weeks/months |
| `recurrenceEndDate` | string (yyyy-MM-dd) | Required if recurring | Must be after startDateTime | Last date of the recurring series |

**`invitees` object fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `email` | string | **Yes** | Invitee's email — Zoho sends them a join link |
| `name` | string | No | Display name shown in the Zoho meeting |

> **How invitations work:** After the meeting is created, the backend makes a **second API call** to Zoho's dedicated panelists endpoint (`POST /sessions/{key}/panelists.json`). Zoho then emails each invitee a personalised join link (panelist role) — no Zoho account needed. Emails are **not** sent when panelists are embedded in the session creation body; the separate call is required.

**With invitees:**
```bash
curl -X POST http://localhost:8090/api/admin/teams/meetings \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation",
    "description": "Welcome session for all new students. Please join on time.",
    "startDateTime": "2026-04-17T10:00:00",
    "endDateTime": "2026-04-17T11:30:00",
    "invitees": [
      { "name": "John Doe", "email": "john@example.com" },
      { "name": "Jane Smith", "email": "jane@example.com" },
      { "email": "student3@example.com" }
    ]
  }'
```

**Without invitees (share `joinUrl` manually):**
```bash
curl -X POST http://localhost:8090/api/admin/teams/meetings \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation",
    "description": "Welcome session for all new students. Please join on time.",
    "startDateTime": "2026-04-17T10:00:00",
    "endDateTime": "2026-04-17T11:30:00"
  }'
```

**Recurring meeting (daily, every 1 day, ends 2026-04-23):**
```bash
curl -X POST http://localhost:8090/api/admin/teams/meetings \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Daily Cybersecurity Stand-up",
    "startDateTime": "2026-04-17T10:00:00",
    "endDateTime": "2026-04-17T11:00:00",
    "recurring": true,
    "repeatType": "DAILY",
    "repeatEvery": 1,
    "recurrenceEndDate": "2026-04-23"
  }'
```

### Success Response (201 Created)

```json
{
  "id": 1,
  "subject": "Cybersecurity Batch 1 — Orientation",
  "description": "Welcome session for all new students. Please join on time.",
  "startDateTime": "2026-04-17T10:00:00",
  "endDateTime": "2026-04-17T11:30:00",
  "meetingId": "1355391166",
  "joinUrl": "https://meeting.cyberlearnix.com/ntxt-oyz-bxc",
  "password": "rSv6vP",
  "duration": "1 hr 30 min",
  "status": "SCHEDULED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-15T13:45:07.189",
  "invitees": [
    { "name": "John Doe", "email": "john@example.com" },
    { "name": "Jane Smith", "email": "jane@example.com" },
    { "name": null, "email": "student3@example.com" }
  ],
  "recurring": false,
  "repeatType": null,
  "repeatEvery": null,
  "recurrenceEndDate": null
}
```

**Recurring meeting response:**
```json
{
  "id": 3,
  "subject": "Daily Cybersecurity Stand-up",
  "startDateTime": "2026-04-17T10:00:00",
  "endDateTime": "2026-04-17T11:00:00",
  "meetingId": "1355391300",
  "joinUrl": "https://meeting.cyberlearnix.com/abcd-efg-hij",
  "password": "xy12z",
  "duration": "1 hr",
  "status": "SCHEDULED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-15T13:45:07.189",
  "invitees": [],
  "recurring": true,
  "repeatType": "DAILY",
  "repeatEvery": 1,
  "recurrenceEndDate": "2026-04-23"
}
```

**Without invitees — `invitees` is an empty array:**
```json
{
  "id": 2,
  "subject": "Cybersecurity Batch 1 — Orientation",
  "meetingId": "1355391167",
  "joinUrl": "https://meeting.cyberlearnix.com/ajux-avy-ray",
  "password": "abc123",
  "duration": "1 hr 30 min",
  "status": "SCHEDULED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-15T13:45:07.189",
  "invitees": [],
  "recurring": false,
  "repeatType": null,
  "repeatEvery": null,
  "recurrenceEndDate": null
}
```

### Error Responses

**400 — Invalid invitee email:**
```json
{
  "error": "Invalid request",
  "message": "invitees[1].email: Invitee email must be valid"
}
```

**400 — Validation failed (missing or blank subject):**
```json
{
  "error": "Invalid request",
  "message": "Meeting subject is required"
}
```

**400 — startDateTime not in the future:**
```json
{
  "error": "Invalid request",
  "message": "Start date/time must be in the future"
}
```

**400 — endDateTime is before or equal to startDateTime:**
```json
{
  "error": "Invalid request",
  "message": "End date/time must be after start date/time"
}
```

**500 — Zoho API error (e.g., expired token or quota issue):**
```json
{
  "error": "Failed to schedule meeting",
  "message": "Zoho API error [401]: ..."
}
```

---

## 2. Get All Meetings

`GET /api/admin/teams/meetings`

Returns all meetings stored in the database, sorted by start time ascending.

### Query Parameters (Filters)

| Parameter | Required | Values | Description |
|-----------|----------|--------|-------------|
| `status` | No | `SCHEDULED`, `CANCELLED` | Filter meetings by status. Omit to get all. |

```bash
# Get ALL meetings (both scheduled and cancelled)
curl http://localhost:8090/api/admin/teams/meetings \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b"

# Get only SCHEDULED (upcoming/active) meetings
curl "http://localhost:8090/api/admin/teams/meetings?status=SCHEDULED" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b"

# Get only CANCELLED meetings
curl "http://localhost:8090/api/admin/teams/meetings?status=CANCELLED" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b"
```

### Success Response (200 OK)

```json
[
  {
    "id": 1,
    "subject": "Cybersecurity Batch 1 — Orientation",
    "description": "Welcome session for all new students.",
    "startDateTime": "2026-04-17T10:00:00",
    "endDateTime": "2026-04-17T11:30:00",
    "meetingId": "1355391166",
    "joinUrl": "https://meeting.cyberlearnix.com/ntxt-oyz-bxc",
    "password": "rSv6vP",
    "duration": "1 hr 30 min",
    "status": "SCHEDULED",
    "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
    "createdAt": "2026-04-15T13:45:07.189",
    "invitees": [
      { "name": "John Doe", "email": "john@example.com" },
      { "name": "Jane Smith", "email": "jane@example.com" }
    ]
  },
  {
    "id": 2,
    "subject": "AI Batch — Live Q&A Session",
    "description": "Open Q&A for AI course week 2.",
    "startDateTime": "2026-04-20T16:00:00",
    "endDateTime": "2026-04-20T17:00:00",
    "meetingId": "1355391200",
    "joinUrl": "https://meeting.cyberlearnix.com/bxyz-123-abc",
    "password": "xyz789",
    "duration": "1 hr",
    "status": "SCHEDULED",
    "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
    "createdAt": "2026-04-16T09:00:00.000",
    "invitees": []
  }
]
```

**Empty list (no meetings):**
```json
[]
```

---

## 3. Get Single Meeting

`GET /api/admin/teams/meetings/{id}`

Fetch details of one meeting by its local database ID.

**Path Parameter:** `id` — the meeting ID (number returned when creating the meeting)

```bash
curl http://localhost:8090/api/admin/teams/meetings/1 \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b"
```

### Success Response (200 OK)

```json
{
  "id": 1,
  "subject": "Cybersecurity Batch 1 — Orientation",
  "description": "Welcome session for all new students.",
  "startDateTime": "2026-04-17T10:00:00",
  "endDateTime": "2026-04-17T11:30:00",
  "meetingId": "1355391166",
  "joinUrl": "https://meeting.cyberlearnix.com/ntxt-oyz-bxc",
  "password": "rSv6vP",
  "duration": "1 hr 30 min",
  "status": "SCHEDULED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-15T13:45:07.189",
  "invitees": [
    { "name": "John Doe", "email": "john@example.com" }
  ]
}
```

### Error Responses

**404 — Meeting not found:**
```
(empty 404 response)
```

---

## 4. Update Meeting

`PUT /api/admin/teams/meetings/{id}`

Reschedule or update the subject/description/invitees of an existing meeting. Updates are synced to Zoho as well.

**Path Parameter:** `id` — the meeting ID

> **Important:** You cannot update a `CANCELLED` meeting. The `joinUrl` remains the same after update.

### Request Body

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `subject` | string | **Yes** | New meeting title |
| `startDateTime` | string (ISO 8601) | **Yes** | New start time (must be in future) |
| `endDateTime` | string (ISO 8601) | **Yes** | New end time (up to 24 hours after startDateTime) |
| `description` | string | No | New agenda/description |
| `invitees` | array of objects | No | See invitee behaviour table below |

### Invitee Update Behaviour

| What you send | Result |
|--------------|--------|
| Omit `invitees` field entirely | Existing invitees are **kept unchanged** — no Zoho call made |
| `"invitees": [ {email}, {email} ]` | **Diff applied** — only newly added emails receive a Zoho invite email; removed emails are deleted from Zoho one by one |
| `"invitees": []` | **All invitees removed** — each existing email is deleted from Zoho |

> **How the diff works:** The backend compares the stored list against the new list. Emails that appear in the new list but not the old list are POSTed to Zoho (which sends them an invitation email). Emails that were in the old list but are missing from the new list are DELETEd from Zoho individually. Unchanged emails are untouched — no duplicate emails sent.

---

**Case 1 — Reschedule only (keep existing invitees):**
```bash
curl -X PUT http://localhost:8090/api/admin/teams/meetings/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation (Rescheduled)",
    "description": "Rescheduled to next day. Same link works.",
    "startDateTime": "2026-04-18T14:00:00",
    "endDateTime": "2026-04-18T15:30:00"
  }'
```

**Case 2 — Remove one specific email (john removed, others kept):**
```bash
# Current invitees: john@example.com, jane@example.com, bob@example.com
# To remove john — send the list WITHOUT him:
curl -X PUT http://localhost:8090/api/admin/teams/meetings/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation (Rescheduled)",
    "startDateTime": "2026-04-18T14:00:00",
    "endDateTime": "2026-04-18T15:30:00",
    "invitees": [
      { "name": "Jane Smith", "email": "jane@example.com" },
      { "name": "Bob", "email": "bob@example.com" }
    ]
  }'
```

**Case 3 — Add a new invitee (keep existing + add one more):**
```bash
# Current invitees: jane@example.com
# Add alice — send full desired list including jane:
curl -X PUT http://localhost:8090/api/admin/teams/meetings/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation",
    "startDateTime": "2026-04-18T14:00:00",
    "endDateTime": "2026-04-18T15:30:00",
    "invitees": [
      { "name": "Jane Smith", "email": "jane@example.com" },
      { "name": "Alice", "email": "alice@example.com" }
    ]
  }'
```

**Case 4 — Remove ALL invitees:**
```bash
curl -X PUT http://localhost:8090/api/admin/teams/meetings/1 \
  -H "Content-Type: application/json" \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b" \
  -d '{
    "subject": "Cybersecurity Batch 1 — Orientation",
    "startDateTime": "2026-04-18T14:00:00",
    "endDateTime": "2026-04-18T15:30:00",
    "invitees": []
  }'
```

### Success Response (200 OK)

```json
{
  "id": 1,
  "subject": "Cybersecurity Batch 1 — Orientation (Rescheduled)",
  "description": "Rescheduled to next day. Same link works.",
  "startDateTime": "2026-04-18T14:00:00",
  "endDateTime": "2026-04-18T15:30:00",
  "meetingId": "1355391166",
  "joinUrl": "https://meeting.cyberlearnix.com/ntxt-oyz-bxc",
  "password": "rSv6vP",
  "duration": "1 hr 30 min",
  "status": "SCHEDULED",
  "createdBy": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
  "createdAt": "2026-04-15T13:45:07.189",
  "invitees": [
    { "name": "Jane Smith", "email": "jane@example.com" },
    { "name": "Bob", "email": "bob@example.com" }
  ]
}
```

### Error Responses

**400 — Trying to update a cancelled meeting:**
```json
{
  "error": "Invalid operation",
  "message": "Cannot update a cancelled meeting"
}
```

**400 — Invalid invitee email:**
```json
{
  "error": "Invalid request",
  "message": "invitees[0].email: Invitee email must be valid"
}
```

**400 — Invalid date (end before start):**
```json
{
  "error": "Invalid request",
  "message": "End date/time must be after start date/time"
}
```

**404 — Meeting not found:**
```
(empty 404 response)
```

**500 — Zoho sync failed:**
```json
{
  "error": "Failed to update meeting",
  "message": "Zoho API error [404]: Session not found"
}
```

---

## 5. Cancel Meeting

`DELETE /api/admin/teams/meetings/{id}`

Cancels the meeting in Zoho and marks its status as `CANCELLED` locally. The `joinUrl` becomes invalid after this.

**Path Parameter:** `id` — the meeting ID

> **Warning:** This cannot be undone. Cancelled meetings cannot be re-activated.

```bash
curl -X DELETE http://localhost:8090/api/admin/teams/meetings/1 \
  -H "X-User-Role: admin" \
  -H "X-User-Id: 9f4ea44b-966c-4e76-9646-bae02bfc116b"
```

### Success Response (200 OK)

```json
{
  "message": "Meeting cancelled successfully"
}
```

### Error Responses

**400 — Meeting is already cancelled:**
```json
{
  "error": "Invalid operation",
  "message": "Meeting is already cancelled"
}
```

**404 — Meeting not found:**
```
(empty 404 response)
```

**500 — Zoho cancellation failed:**
```json
{
  "error": "Failed to cancel meeting",
  "message": "Zoho API error [403]: ..."
}
```

---

## Response Object Reference

All endpoints return meetings in this shape:

| Field | Type | Description |
|-------|------|-------------|
| `id` | number | Local database ID |
| `subject` | string | Meeting title/topic |
| `description` | string \| null | Meeting agenda/description |
| `startDateTime` | string (ISO 8601) | Scheduled start time (IST) |
| `endDateTime` | string (ISO 8601) | Scheduled end time (IST) |
| `meetingId` | string | Zoho Meeting ID (shown to participants as "Meeting ID") |
| `joinUrl` | string | **Share this with students** — click to join, no Zoho account needed |
| `password` | string \| null | Meeting password — share alongside `joinUrl` |
| `duration` | string | Human-readable duration e.g. `"1 hr"`, `"1 hr 30 min"`, `"45 min"` |
| `status` | string | `SCHEDULED` or `CANCELLED` |
| `createdBy` | string | Admin user ID who created the meeting |
| `createdAt` | string (ISO 8601) | When the meeting record was created |
| `invitees` | array | List of invited participants. Empty array `[]` if no invitees. |
| `recurring` | boolean | Whether this is a recurring meeting |
| `repeatType` | string \| null | `DAILY`, `WEEKLY`, `MONTHLY` — null for one-time meetings |
| `repeatEvery` | number \| null | Recurrence interval (e.g. `1` = every day) |
| `recurrenceEndDate` | string \| null | Last date of the series (`yyyy-MM-dd`) |

**`invitees` item shape:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | string \| null | Invitee display name (optional) |
| `email` | string | Invitee email address |

---

## Frontend Integration Guide

### How to Display Meetings List

```javascript
// Fetch all upcoming meetings
const response = await fetch('http://localhost:8090/api/admin/teams/meetings?status=SCHEDULED', {
  headers: {
    'X-User-Role': 'admin',
    'X-User-Id': adminUserId,
    'Authorization': `Bearer ${jwtToken}`
  }
});
const meetings = await response.json();

// meetings is an array — each has a joinUrl to share with students
meetings.forEach(meeting => {
  console.log(meeting.subject, meeting.joinUrl, meeting.startDateTime);
});
```

### How to Create a Meeting (Without Invitees)

```javascript
const body = {
  subject: "Live Session: Module 3",
  description: "We'll cover SQL injection and XSS attacks.",
  startDateTime: "2026-04-20T18:00:00",
  endDateTime: "2026-04-20T19:30:00"
};

const response = await fetch('http://localhost:8090/api/admin/teams/meetings', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Role': 'admin',
    'X-User-Id': adminUserId,
    'Authorization': `Bearer ${jwtToken}`
  },
  body: JSON.stringify(body)
});

const meeting = await response.json();

if (response.status === 201) {
  // meeting.joinUrl — share with students via notification/email
  console.log("Share this link with students:", meeting.joinUrl);
} else {
  console.error("Failed:", meeting.message);
}
```

### How to Invite Participants (Send Email Invitations)

Add an `invitees` array to automatically have Zoho email each participant a personalised join link:

```javascript
const body = {
  subject: "Live Session: Module 3",
  description: "We'll cover SQL injection and XSS attacks.",
  startDateTime: "2026-04-20T18:00:00",
  endDateTime: "2026-04-20T19:30:00",
  invitees: [
    { name: "John Doe", email: "john@example.com" },
    { name: "Jane Smith", email: "jane@example.com" },
    { email: "anonymous@example.com" }   // name is optional
  ]
};

const response = await fetch('http://localhost:8090/api/admin/teams/meetings', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-User-Role': 'admin',
    'X-User-Id': adminUserId,
    'Authorization': `Bearer ${jwtToken}`
  },
  body: JSON.stringify(body)
});

const meeting = await response.json();
// meeting.invitees — the saved list of invited participants
// Each invitee receives a Zoho email with their own join link automatically
```

### How to Add an "Invite Participants" Form (UI Pattern)

```javascript
// State for the invite form
const [invitees, setInvitees] = useState([{ name: '', email: '' }]);

// Add a new empty row
const addInvitee = () => setInvitees([...invitees, { name: '', email: '' }]);

// Remove a specific row by index (this is how you delete an invitee)
const removeInvitee = (index) => setInvitees(invitees.filter((_, i) => i !== index));

// On submit — filter out empty rows before sending
const payload = {
  subject,
  startDateTime,
  endDateTime,
  description,
  invitees: invitees
    .filter(p => p.email.trim() !== '')   // skip blank rows
    .map(p => ({ name: p.name || undefined, email: p.email.trim() }))
};
```

### How to Remove a Specific Invitee on Edit

When editing a meeting, load the existing `invitees` from the GET response, let the user remove rows, then PUT only those remaining:

```javascript
// 1. Load existing meeting
const meeting = await fetchMeeting(id);

// 2. Pre-fill the form with existing invitees
const [invitees, setInvitees] = useState(meeting.invitees);

// 3. User clicks "Remove" next to john@example.com
const removeInvitee = (index) => setInvitees(invitees.filter((_, i) => i !== index));

// 4. On save — send the remaining list (john is no longer in it)
await fetch(`http://localhost:8090/api/admin/teams/meetings/${id}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', 'X-User-Role': 'admin', 'X-User-Id': adminUserId },
  body: JSON.stringify({
    subject: meeting.subject,
    startDateTime: meeting.startDateTime,
    endDateTime: meeting.endDateTime,
    description: meeting.description,
    invitees  // the filtered list — john is gone
  })
});
// Response will have invitees: [ jane, bob ] without john
```

> **Key rule (diff-based update):** The backend diffs the new `invitees` array against what's stored. **Only newly added emails** are POSTed to Zoho (they receive an invitation email). **Only removed emails** are DELETEd from Zoho (their access is revoked). Unchanged emails are left alone — no duplicate emails sent. To keep the existing list completely unchanged, leave out the `invitees` field entirely.

### How to Leave Invitees Unchanged on Reschedule

If you only want to change the time/subject and **not touch** the invitee list, simply omit `invitees`:

```javascript
await fetch(`http://localhost:8090/api/admin/teams/meetings/${id}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', 'X-User-Role': 'admin', 'X-User-Id': adminUserId },
  body: JSON.stringify({
    subject: 'Updated Title',
    startDateTime: '2026-04-20T10:00:00',
    endDateTime: '2026-04-20T11:00:00'
    // no invitees field — existing invitees are preserved
  })
});
```

### Displaying Invitees on the Meeting Detail Page

```javascript
// meeting.invitees is always an array (empty [] if no invitees)
const meeting = await fetchMeeting(id);

if (meeting.invitees.length > 0) {
  meeting.invitees.forEach(p => {
    console.log(`${p.name ?? 'Guest'} — ${p.email}`);
  });
} else {
  console.log('No invitees. Share joinUrl manually.');
}
```

### How to Share joinUrl with Students

After creating a meeting, the `joinUrl` from the response should be:
- Sent to students via email/notification
- Displayed in their dashboard under "Upcoming Live Sessions"
- Linked from the course page

Students **do not need a Zoho account** — they just click the URL to join via browser.

> **Difference between `joinUrl` and invitee emails:**
> - `joinUrl` — a public link anyone can use if you share it manually
> - `invitees` — Zoho proactively emails each person their own personalised link

---

## Zoho Backend — How It Works (For Reference)

```
Frontend/Admin
     |
     | POST /api/admin/teams/meetings
     v
Admin Service (port 8090)
     |
     | ZohoTokenService.getAccessToken()
     | → Calls Zoho accounts.zoho.in/oauth/v2/token with refresh_token
     | → Returns valid access_token (cached 1 hour, auto-refreshed)
     |
     | POST https://meeting.zoho.in/api/v2/{orgId}/sessions.json
     | Authorization: Zoho-oauthtoken <access_token>
     v
Zoho Meeting API
     |
     | Returns: session.meetingKey + session.joinLink
     v
Admin Service
     | Saves to DB (teams_meetings table)
     | Returns meeting object with joinUrl
     v
Frontend
```

### Zoho Configuration (Environment Variables)

> These are configured in `application.properties` and can be overridden via environment variables.

| Property | Env Variable | Description |
|----------|-------------|-------------|
| `zoho.client-id` | `ZOHO_CLIENT_ID` | Your Zoho API Console client ID |
| `zoho.client-secret` | `ZOHO_CLIENT_SECRET` | Your Zoho client secret |
| `zoho.refresh-token` | `ZOHO_REFRESH_TOKEN` | Long-lived refresh token (never expires) |
| `zoho.org-id` | `ZOHO_ORG_ID` | Zoho organization ID |
| `zoho.host-zuid` | `ZOHO_HOST_ZUID` | Zoho User ID of the meeting host |
| `zoho.accounts-url` | `ZOHO_ACCOUNTS_URL` | `https://accounts.zoho.in` (India DC) |
| `zoho.meeting-api-url` | `ZOHO_MEETING_API_URL` | `https://meeting.zoho.in/api/v2` (India DC) |

### One-Time Zoho Setup (For DevOps/Backend)

1. Go to [https://api-console.zoho.com](https://api-console.zoho.com)
2. Add Client → Server-based Applications
3. Generate a Self-Client code with scope: `ZohoMeeting.meeting.ALL`
4. Exchange for tokens:
```bash
curl -X POST "https://accounts.zoho.in/oauth/v2/token" \
  -d "code=<authorization_code>" \
  -d "client_id=<client_id>" \
  -d "client_secret=<client_secret>" \
  -d "redirect_uri=<redirect_uri>" \
  -d "grant_type=authorization_code"
```
5. Save the `refresh_token` from the response as `ZOHO_REFRESH_TOKEN` env var

---

## Quick Reference — All 5 Endpoints

| # | Method | Path | Description |
|---|--------|------|-------------|
| 1 | POST | `/api/admin/teams/meetings` | Create a new meeting |
| 2 | GET | `/api/admin/teams/meetings` | List all meetings (filter: `?status=SCHEDULED\|CANCELLED`) |
| 3 | GET | `/api/admin/teams/meetings/{id}` | Get a single meeting by ID |
| 4 | PUT | `/api/admin/teams/meetings/{id}` | Update/reschedule a meeting |
| 5 | DELETE | `/api/admin/teams/meetings/{id}` | Cancel a meeting |

---

## Error Reference

| Status | When It Happens | What to Show User |
|--------|----------------|------------------|
| `201` | Meeting created successfully | Show joinUrl, copy button |
| `200` | Request succeeded | — |
| `400` | Invalid input (bad dates, blank subject, already cancelled) | Show `message` from response |
| `404` | Meeting ID doesn't exist | "Meeting not found" |
| `403` | Not admin role | Redirect to login |
| `500` | Zoho API failed (token expired, quota, network) | "Could not reach meeting service, please try again" |
