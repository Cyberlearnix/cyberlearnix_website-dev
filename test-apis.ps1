param([string]$Token = $env:API_TOKEN)

$base = "http://localhost:8080"
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
T GET "/api/course-management/courses" 200
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
T POST "/api/enrollments" 400 $newEnroll -label "(create with bad ids - expect 400 or 201)"

Write-Host "`n=== 6. COUPON ENDPOINTS ===" -ForegroundColor Cyan
T GET "/api/enrollments/coupons" 200 -label "(list coupons - admin)"
$newCoupon = '{"code":"TEST10","discountPercent":10,"maxUses":5}'
$cr2 = T POST "/api/enrollments/coupons" 201 $newCoupon -label "(create coupon - admin)"

Write-Host "`n=== 7. STUDENT DASHBOARD ===" -ForegroundColor Cyan
T GET "/api/student/dashboard"    200 -label "(as admin)"
T GET "/api/student/enrollments"  200 -label "(as admin)"

Write-Host "`n=== 8. ADMIN ENDPOINTS ===" -ForegroundColor Cyan
T GET "/api/admin/stats/revenue"  200 -label "(revenue stats)"
T GET "/api/admin/users"          200 -label "(user management)"

Write-Host "`n=== 9. NOTIFICATION SERVICE ===" -ForegroundColor Cyan
T GET "/api/notifications"        200 -label "(list notifications)"

Write-Host "`n=== 10. SHOP SERVICE ===" -ForegroundColor Cyan
T GET "/api/shop/products"        200 -label "(list products)"

Write-Host "`n=== 11. INSTRUCTOR SERVICE ===" -ForegroundColor Cyan
T GET "/api/instructors"          200 -label "(list instructors)"

Write-Host "`n=== 12. FORM SERVICE ===" -ForegroundColor Cyan
T GET "/api/forms"                200 -label "(list forms)"

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "RESULTS: PASS=$pass  WARN=$warn  FAIL=$fail" -ForegroundColor $(if ($fail -gt 0) {"Red"} elseif ($warn -gt 0) {"Yellow"} else {"Green"})
