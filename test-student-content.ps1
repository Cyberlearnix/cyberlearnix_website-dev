#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Tests the student content loading flow: enrollment check + curriculum + certificates.

.PARAMETER Email
  Student (or admin) email to login with.

.PARAMETER Password
  Password (prompted if omitted).

.PARAMETER CourseId
  Course ID to test curriculum access for. Default: 7

.PARAMETER Base
  API base URL. Default: https://apis.cyberlearnix.com
#>
param(
    [string]$Email    = $env:API_EMAIL,
    [string]$Password = $env:API_PASSWORD,
    [int]   $CourseId = 7,
    [string]$Base     = "https://apis.cyberlearnix.com"
)

$pass = 0; $fail = 0; $warn = 0

function Log-Result([string]$status, [string]$msg) {
    $color = switch ($status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "WARN" { "Yellow" }
        default { "White" }
    }
    Write-Host "  $status  $msg" -ForegroundColor $color
    switch ($status) {
        "PASS" { $script:pass++ }
        "FAIL" { $script:fail++ }
        "WARN" { $script:warn++ }
    }
}

function Invoke-API([string]$method, [string]$path, [hashtable]$headers = @{}, [string]$body = $null) {
    $uri = "$Base$path"
    $p = @{ Uri=$uri; Method=$method; Headers=$headers; UseBasicParsing=$true; TimeoutSec=15 }
    if ($body) { $p.Body = $body; $p.ContentType = "application/json" }
    try {
        $resp = Invoke-WebRequest @p
        return [PSCustomObject]@{ Status=$resp.StatusCode; Body=$resp.Content; Ok=$true }
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        $errBody = ""
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $errBody = [System.IO.StreamReader]::new($stream).ReadToEnd()
        } catch {}
        return [PSCustomObject]@{ Status=$code; Body=$errBody; Ok=$false }
    }
}

Write-Host "`n===========================================================" -ForegroundColor Cyan
Write-Host "  Cyberlearnix Student Content Loading Test" -ForegroundColor Cyan
Write-Host "  API: $Base | Course: $CourseId" -ForegroundColor Cyan
Write-Host "===========================================================`n"

# ── 1. Login ─────────────────────────────────────────────────────────────────
if (-not $Email) { $Email = Read-Host "Email" }
if (-not $Password) { $Password = Read-Host "Password" -AsSecureString | ForEach-Object { [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($_)) } }

Write-Host "── 1. Authentication ───────────────────────────────────────" -ForegroundColor Cyan
$loginBody = "{`"email`":`"$Email`",`"password`":`"$Password`"}"
$loginResp = Invoke-API "POST" "/api/auth/login" -body $loginBody
if ($loginResp.Ok -and $loginResp.Status -eq 200) {
    $loginData = $loginResp.Body | ConvertFrom-Json
    $token = $loginData.token
    $userId = $loginData.userId ?? $loginData.id ?? $loginData.user?.id
    $role   = $loginData.role ?? $loginData.user?.role
    Log-Result "PASS" "Login OK | userId=$userId | role=$role"
    if (-not $token) {
        Log-Result "FAIL" "No token in login response — cannot continue"
        exit 1
    }
} else {
    Log-Result "FAIL" "Login failed [$($loginResp.Status)]: $($loginResp.Body)"
    exit 1
}

$authHeaders = @{ Authorization = "Bearer $token" }

# ── 2. Student Dashboard ─────────────────────────────────────────────────────
Write-Host "`n── 2. Student Dashboard (enrolled courses) ─────────────────" -ForegroundColor Cyan
$dashResp = Invoke-API "GET" "/api/student/enrollments" $authHeaders
if ($dashResp.Ok) {
    $dashData = $dashResp.Body | ConvertFrom-Json
    $enrollments = $dashData.enrollments ?? $dashData
    Log-Result "PASS" "Student dashboard loaded | enrollments=$(@($enrollments).Count)"
    $courseEnrollment = @($enrollments) | Where-Object { $_.courseId -eq $CourseId }
    if ($courseEnrollment) {
        Log-Result "PASS" "Course $CourseId in enrollment list | progress=$($courseEnrollment.progress)%"
    } else {
        Log-Result "WARN" "Course $CourseId NOT found in student enrollment list"
        Write-Host "         Enrolled course IDs: $((@($enrollments) | ForEach-Object { $_.courseId }) -join ', ')" -ForegroundColor Gray
    }
} else {
    Log-Result "FAIL" "Student dashboard failed [$($dashResp.Status)]: $($dashResp.Body.Substring(0,[Math]::Min(200,$dashResp.Body.Length)))"
}

# ── 3. Enrollment Check (direct, no auth — should return true/false) ─────────
Write-Host "`n── 3. Enrollment Check (GET /api/enrollments/check) ────────" -ForegroundColor Cyan
if ($userId) {
    $checkResp = Invoke-API "GET" "/api/enrollments/check?studentId=$userId&courseId=$CourseId"
    if ($checkResp.Ok) {
        $isEnrolled = $checkResp.Body.Trim()
        if ($isEnrolled -eq "true") {
            Log-Result "PASS" "Enrollment check: student IS enrolled in course $CourseId"
        } else {
            Log-Result "WARN" "Enrollment check: student is NOT enrolled in course $CourseId (this will cause 403 on curriculum)"
            Write-Host "         ACTION NEEDED: Use admin API to create enrollment:" -ForegroundColor Yellow
            Write-Host "         POST /api/enrollments with body: {`"studentId`":`"$userId`",`"courseId`":$CourseId}" -ForegroundColor Yellow
        }
    } else {
        Log-Result "FAIL" "Enrollment check failed [$($checkResp.Status)] — gateway may not yet have the whitelist fix deployed"
        Write-Host "         Note: After deploying the gateway fix, this should work without auth" -ForegroundColor Gray
    }
} else {
    Log-Result "WARN" "Could not extract userId from login response — skipping enrollment check"
}

# ── 4. Course Curriculum ──────────────────────────────────────────────────────
Write-Host "`n── 4. Course Curriculum (GET /api/courses/$CourseId/curriculum) ─" -ForegroundColor Cyan
$currResp = Invoke-API "GET" "/api/courses/$CourseId/curriculum" $authHeaders
if ($currResp.Ok) {
    $currData = $currResp.Body | ConvertFrom-Json
    $modules  = $currData.modules ?? @()
    $totalContent = 0
    foreach ($m in $modules) { $totalContent += @($m.contents).Count }
    Log-Result "PASS" "Curriculum loaded | modules=$(@($modules).Count) | total content=$totalContent"
    if ($totalContent -eq 0) {
        Log-Result "WARN" "Curriculum returned 0 content items — check if content was added via admin LMS"
    }
} else {
    $body = $currResp.Body
    $errMsg = try { ($body | ConvertFrom-Json).error } catch { $body }
    Log-Result "FAIL" "Curriculum failed [$($currResp.Status)]: $errMsg"
    if ($currResp.Status -eq 403) {
        Write-Host "         → 403 means student is NOT enrolled in course $CourseId" -ForegroundColor Red
        Write-Host "         → Run: POST /api/enrollments {`"studentId`":`"$userId`",`"courseId`":$CourseId} with admin token" -ForegroundColor Red
    } elseif ($currResp.Status -eq 503) {
        Write-Host "         → 503 means enrollment-service is unreachable from course-service" -ForegroundColor Red
    }
}

# ── 5. Student Certificates ───────────────────────────────────────────────────
Write-Host "`n── 5. Student Certificates (GET /api/certificates/student) ─" -ForegroundColor Cyan
$certResp = Invoke-API "GET" "/api/certificates/student" $authHeaders
if ($certResp.Ok) {
    $certs = try { $certResp.Body | ConvertFrom-Json } catch { @() }
    Log-Result "PASS" "Certificates endpoint OK | count=$(@($certs).Count)"
} else {
    Log-Result "FAIL" "Certificates/student failed [$($certResp.Status)]"
    if ($certResp.Status -eq 500) {
        Write-Host "         → 500 on /api/certificates/student — need to deploy course-service fix" -ForegroundColor Red
    }
}

# ── 6. Create Enrollment if Missing (admin only) ──────────────────────────────
Write-Host "`n── 6. Admin: Ensure Enrollment Exists ──────────────────────" -ForegroundColor Cyan
if ($role -eq "admin" -and $userId) {
    # Check if enrollment is already there
    $checkResp2 = Invoke-API "GET" "/api/enrollments/check?studentId=$userId&courseId=$CourseId"
    $alreadyEnrolled = ($checkResp2.Ok -and $checkResp2.Body.Trim() -eq "true")

    if ($alreadyEnrolled) {
        Log-Result "PASS" "Enrollment already exists — no action needed"
    } else {
        Write-Host "  Creating enrollment for studentId=$userId courseId=$CourseId..." -ForegroundColor Yellow
        $enrollBody = "{`"studentId`":`"$userId`",`"courseId`":$CourseId}"
        $createResp = Invoke-API "POST" "/api/enrollments" $authHeaders $enrollBody
        if ($createResp.Status -in 200, 201) {
            Log-Result "PASS" "Enrollment created successfully"
        } elseif ($createResp.Status -eq 409) {
            Log-Result "PASS" "Enrollment already exists (conflict = already enrolled)"
        } else {
            Log-Result "FAIL" "Enrollment creation failed [$($createResp.Status)]: $($createResp.Body.Substring(0,[Math]::Min(200,$createResp.Body.Length)))"
        }
    }
} else {
    Log-Result "WARN" "Skipping enrollment creation — need admin role (current: $role)"
    Write-Host "         To create enrollment, login as admin and re-run this script" -ForegroundColor Gray
}

# ── 7. Re-test Curriculum after enrollment ────────────────────────────────────
Write-Host "`n── 7. Re-test Curriculum After Enrollment ──────────────────" -ForegroundColor Cyan
$currResp2 = Invoke-API "GET" "/api/courses/$CourseId/curriculum" $authHeaders
if ($currResp2.Ok) {
    $currData2 = $currResp2.Body | ConvertFrom-Json
    $modules2  = $currData2.modules ?? @()
    $totalContent2 = 0
    foreach ($m in $modules2) { $totalContent2 += @($m.contents).Count }
    Log-Result "PASS" "Curriculum re-test OK | modules=$(@($modules2).Count) | total content=$totalContent2"
    if ($totalContent2 -gt 0) {
        Log-Result "PASS" "Content is now visible to student ($totalContent2 items)"
    }
} else {
    $errMsg2 = try { ($currResp2.Body | ConvertFrom-Json).error } catch { $currResp2.Body }
    Log-Result "FAIL" "Curriculum still failing [$($currResp2.Status)]: $errMsg2"
}

# ── Summary ──────────────────────────────────────────────────────────────────
Write-Host "`n===========================================================`n"
Write-Host "  Results: PASS=$pass  FAIL=$fail  WARN=$warn" -ForegroundColor $(if ($fail -gt 0) { "Red" } else { "Green" })
Write-Host ""
if ($fail -gt 0) {
    Write-Host "  NEXT STEPS:" -ForegroundColor Yellow
    Write-Host "   1. Deploy the updated course-service, enrollment-service, gateway-service" -ForegroundColor Yellow
    Write-Host "   2. If enrollment is missing: ensure admin has added the student to the course" -ForegroundColor Yellow
    Write-Host "   3. Re-run this script to verify the fix" -ForegroundColor Yellow
}
Write-Host "===========================================================`n"
exit $(if ($fail -gt 0) { 1 } else { 0 })
