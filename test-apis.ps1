param(
    [string]$Token = $env:API_TOKEN,
    [string]$Email = $env:API_EMAIL,
    [string]$Password = $env:API_PASSWORD,
    [string]$Base = "http://localhost:8080"
)

$base = $Base
$pass = 0; $fail = 0; $warn = 0

function T {
    param([string]$method, [string]$path, [int]$expected = 200, [string]$body = $null, [switch]$noAuth, [string]$label = "")
    $hdr = if ($noAuth) { @{} } else { @{ Authorization = "Bearer $Token" } }
    try {
        $p = @{Uri="$base$path"; Method=$method; Headers=$hdr; UseBasicParsing=$true; TimeoutSec=10}
        if ($body) { $p.Body = $body; $p.ContentType = "application/json" }
        $res = Invoke-WebRequest @p
        if ($res.StatusCode -eq $expected) {
            Write-Host "PASS [$($res.StatusCode)] $method $path $label" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "WARN [$($res.StatusCode)] $method $path (expected $expected) $label" -ForegroundColor Yellow
            $script:warn++
        }
        return $res
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq $expected) {
            Write-Host "PASS [$code] $method $path $label" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "FAIL [$code] $method $path (expected $expected) $label" -ForegroundColor Red
            $script:fail++
        }
        return $null
    }
}

Write-Host "`n=== 1. PUBLIC ENDPOINTS (no auth required) ===" -ForegroundColor Cyan
T GET "/api/courses"                200 -noAuth
T GET "/actuator/health"            200 -noAuth
T GET "/api/banners"                200 -noAuth   # CMS
T GET "/api/promos"                 200 -noAuth   # CMS

Write-Host "`n=== 2. UNAUTHENTICATED ACCESS BLOCKED ===" -ForegroundColor Cyan
T GET "/api/enrollments"            401 -noAuth
T GET "/api/users/profile"          401 -noAuth
T GET "/api/enrollments/coupons"    401 -noAuth

Write-Host "`n=== 3. AUTH ENDPOINTS ===" -ForegroundColor Cyan
T GET "/api/users/profile"          200 -label "(admin)"
T GET "/api/users"                  200 -label "(admin list)"

Write-Host "`n=== 4. COURSE MANAGEMENT ===" -ForegroundColor Cyan
T GET "/api/admin/courses" 200 -label "(admin list)"
$newCourse = '{"title":"Test Course","description":"API test course","price":0,"status":"DRAFT"}'
$cr = T POST "/api/course-management/courses" 201 $newCourse -label "(create)"
if ($cr) {
    $courseId = ($cr.Content | ConvertFrom-Json).id
    Write-Host "  Created course id: $courseId" -ForegroundColor Gray
    T GET "/api/course-management/courses/$courseId" 200 -label "(get by id)"
    T DELETE "/api/course-management/courses/$courseId" 204 -label "(delete)"
}

Write-Host "`n=== 5. ENROLLMENTS (admin) ===" -ForegroundColor Cyan
T GET "/api/enrollments" 200 -label "(list all - admin)"
$newEnroll = '{"studentId":"test-student-id","courseId":"test-course-id","amountPaid":0}'
T POST "/api/enrollments" 400 $newEnroll -label "(create with bad ids - expect 400)"

Write-Host "`n=== 6. COUPON ENDPOINTS ===" -ForegroundColor Cyan
T GET "/api/enrollments/coupons" 200 -label "(list coupons - admin)"
$newCoupon = '{"code":"TESTAPI1","discountType":"PERCENTAGE","discountValue":10,"maxUsages":5,"expiresAt":"2027-12-31T23:59:59"}'
$cr2 = T POST "/api/enrollments/coupons" 200 $newCoupon -label "(create coupon - admin)"

Write-Host "`n=== 7. STUDENT DASHBOARD ===" -ForegroundColor Cyan
T GET "/api/student/dashboard"    200 -label "(as admin)"
T GET "/api/student/enrollments"  200 -label "(as admin)"

Write-Host "`n=== 8. ADMIN ENDPOINTS ===" -ForegroundColor Cyan
T GET "/api/admin/reports/revenue" 200 -label "(revenue stats)"
T GET "/api/admin/users"          200 -label "(user management)"

Write-Host "`n=== 9. NOTIFICATION SERVICE ===" -ForegroundColor Cyan
T GET "/api/notifications/inbox"   200 -label "(inbox notifications)"

Write-Host "`n=== 10. SHOP SERVICE ===" -ForegroundColor Cyan
T GET "/api/shop"                  200 -label "(shop settings)"

Write-Host "`n=== 11. INSTRUCTOR SERVICE ===" -ForegroundColor Cyan
T GET "/api/instructor/courses"   200 -label "(instructor courses)"

Write-Host "`n=== 12. FORM SERVICE ===" -ForegroundColor Cyan
T GET "/api/forms"                200 -label "(list forms)"

# ── Auto-login if token not supplied ──────────────────────────────────────────
if (-not $Token -and $Email -and $Password) {
    Write-Host "`nNo token provided -- logging in as $Email ..." -ForegroundColor Gray
    try {
        $lr = Invoke-RestMethod -Uri "$base/api/auth/login" -Method POST `
              -ContentType "application/json" `
              -Body (ConvertTo-Json @{email=$Email; password=$Password}) `
              -UseBasicParsing
        $Token = if ($lr.token) { $lr.token } elseif ($lr.accessToken) { $lr.accessToken } else { $lr.jwt }
        Write-Host "  Got token: $($Token.Substring(0,[Math]::Min(40,$Token.Length)))..." -ForegroundColor Gray
    } catch {
        Write-Host "  Login failed: $_" -ForegroundColor Red
    }
}

Write-Host "`n=== 13. MEDIA UPLOAD ENDPOINTS ===" -ForegroundColor Cyan

function TUpload {
    param([string]$path, [string]$fieldName = "file", [string]$fileName = "test.jpg",
          [byte[]]$bytes, [string]$mimeType = "image/jpeg", [string]$label = "")
    if (-not $Token) { Write-Host "SKIP  $path $label (no token)" -ForegroundColor DarkGray; $script:warn++; return }
    try {
        $boundary = [System.Guid]::NewGuid().ToString()
        $LF = "`r`n"
        $bodyLines = (
            "--$boundary",
            "Content-Disposition: form-data; name=`"$fieldName`"; filename=`"$fileName`"",
            "Content-Type: $mimeType",
            "",
            [System.Text.Encoding]::UTF8.GetString($bytes),
            "--$boundary--"
        ) -join $LF
        $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($bodyLines)
        $res = Invoke-WebRequest -Uri "$base$path" -Method POST `
               -Headers @{Authorization="Bearer $Token"} `
               -ContentType "multipart/form-data; boundary=$boundary" `
               -Body $bodyBytes -UseBasicParsing -TimeoutSec 30
        if ($res.StatusCode -in 200,201) {
            $json = $res.Content | ConvertFrom-Json
            Write-Host "PASS [$($res.StatusCode)] POST $path $label" -ForegroundColor Green
            Write-Host "       fileId=$(($json.fileId,'N/A' | Where-Object {$_} | Select-Object -First 1))  url=$(($json.url,$json.viewUrl,'N/A' | Where-Object {$_} | Select-Object -First 1))" -ForegroundColor Gray
            $script:pass++
        } else {
            Write-Host "WARN [$($res.StatusCode)] POST $path $label" -ForegroundColor Yellow
            $script:warn++
        }
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        $detail = ""
        try { $detail = $_.Exception.Response.GetResponseStream() | ForEach-Object { (New-Object IO.StreamReader $_).ReadToEnd() } } catch {}
        Write-Host "FAIL [$code] POST $path $label  $detail" -ForegroundColor Red
        $script:fail++
    }
}

# 1x1 white JPEG (smallest valid JPEG -- 631 bytes)
$tinyJpeg = [Convert]::FromBase64String(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U" +
    "HRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgN" +
    "DRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy" +
    "MjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAA" +
    "AAAAAAAAAAAAAAAAAP/EABQBAQAAAAAAAAAAAAAAAAAAAAD/xAAUEQEAAAAAAAAAAAAAAAAA" +
    "AAAA/9oADAMBAAIRAxEAPwCwABmX/9k=")

# 1x1 PNG (67 bytes)
$tinyPng = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")

# Check unauthenticated upload is blocked
Write-Host "`n  -- Unauthenticated upload should return 401 --" -ForegroundColor DarkGray
$boundary2 = "boundary123"
$dummyBody = "--$boundary2`r`nContent-Disposition: form-data; name=`"file`"; filename=`"x.jpg`"`r`nContent-Type: image/jpeg`r`n`r`na`r`n--$boundary2--"
try {
    Invoke-WebRequest -Uri "$base/api/materials/upload/thumbnail" -Method POST `
        -ContentType "multipart/form-data; boundary=$boundary2" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($dummyBody)) -UseBasicParsing -TimeoutSec 5 | Out-Null
    Write-Host "FAIL [2xx] POST /api/materials/upload/thumbnail (expected 401 -- auth not enforced!)" -ForegroundColor Red
    $script:fail++
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401) {
        Write-Host "PASS [401] POST /api/materials/upload/thumbnail (unauth correctly blocked)" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "WARN [$code] POST /api/materials/upload/thumbnail unauth (expected 401)" -ForegroundColor Yellow
        $script:warn++
    }
}

# Authenticated uploads
TUpload "/api/materials/upload/thumbnail"    -bytes $tinyJpeg -fileName "thumb.jpg"   -label "(thumbnail image)"
TUpload "/api/materials/upload/module-image" -bytes $tinyPng  -fileName "module.png" -mimeType "image/png" -label "(module image)"
TUpload "/api/materials/upload/banner"       -bytes $tinyJpeg -fileName "banner.jpg"  -label "(banner image -- admin only)"

# Drive endpoint should respond (503 if Drive not configured, 200 if it is)
Write-Host "`n  -- /drive/upload endpoints (expect 200 or 503 depending on Drive config) --" -ForegroundColor DarkGray
if ($Token) {
    try {
        $boundary3 = [System.Guid]::NewGuid().ToString()
        $bodyLines3 = "--$boundary3`r`nContent-Disposition: form-data; name=`"file`"; filename=`"vid.mp4`"`r`nContent-Type: video/mp4`r`n`r`n`r`n--$boundary3--"
        $res3 = Invoke-WebRequest -Uri "$base/api/materials/drive/upload/video" -Method POST `
                -Headers @{Authorization="Bearer $Token"; "X-User-Role"="admin"} `
                -ContentType "multipart/form-data; boundary=$boundary3" `
                -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyLines3)) -UseBasicParsing -TimeoutSec 10
        Write-Host "PASS [$($res3.StatusCode)] POST /api/materials/drive/upload/video (Drive configured)" -ForegroundColor Green
        $script:pass++
    } catch {
        $code3 = $_.Exception.Response.StatusCode.value__
        if ($code3 -eq 503) {
            Write-Host "WARN [503] POST /api/materials/drive/upload/video (Drive NOT configured -- set GOOGLE_DRIVE_CREDENTIALS_JSON_B64 env var)" -ForegroundColor Yellow
            $script:warn++
        } else {
            Write-Host "FAIL [$code3] POST /api/materials/drive/upload/video" -ForegroundColor Red
            $script:fail++
        }
    }
}

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "RESULTS: PASS=$pass  WARN=$warn  FAIL=$fail" -ForegroundColor $(if ($fail -gt 0) {"Red"} elseif ($warn -gt 0) {"Yellow"} else {"Green"})
