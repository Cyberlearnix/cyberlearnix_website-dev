param(
    [string]$Email    = "shivakumar@cyberlearnix.com",
    [string]$Password = "",
    [string]$AuthBase = "https://apis.cyberlearnix.com",
    [string]$LabBase  = "https://apis.cyberlearnix.com"
)

$pass = 0; $fail = 0; $warn = 0
$token = $null

function T {
    param([string]$method,[string]$url,[int]$expected=200,[string]$body=$null,[switch]$noAuth,[string]$label="")
    $hdr = if ($noAuth) { @{} } else { @{ Authorization = "Bearer $token" } }
    try {
        $p = @{Uri=$url;Method=$method;Headers=$hdr;UseBasicParsing=$true;TimeoutSec=30}
        if ($body) { $p.Body=$body; $p.ContentType="application/json" }
        $res = Invoke-WebRequest @p
        $code = $res.StatusCode
        if ($code -eq $expected) {
            Write-Host "  PASS [$code] $method $url $label" -ForegroundColor Green; $script:pass++
        } else {
            Write-Host "  WARN [$code] $method $url (expected $expected) $label" -ForegroundColor Yellow; $script:warn++
        }
        return $res
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq $expected) {
            Write-Host "  PASS [$code] $method $url $label" -ForegroundColor Green; $script:pass++
        } else {
            Write-Host "  FAIL [$code] $method $url (expected $expected) $label" -ForegroundColor Red
            try { $errBody = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream()).ReadToEnd(); Write-Host "        $errBody" -ForegroundColor DarkGray } catch {}
            $script:fail++
        }
        return $null
    }
}

Write-Host "`n=== 1. AUTH - Login to get JWT ===" -ForegroundColor Cyan

if (-not $Password) {
    $secPwd = Read-Host "Password for $Email" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPwd)
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
}

$loginBody = '{"email":"' + $Email + '","password":"' + $Password + '"}'
try {
    $resp = Invoke-RestMethod -Method Post -Uri "$AuthBase/api/auth/login" -ContentType "application/json" -Body $loginBody -ErrorAction Stop
    if ($resp.accessToken) { $token = $resp.accessToken } else { $token = $resp.token }
    $userId = if ($resp.userId) { $resp.userId } elseif ($resp.id) { $resp.id } elseif ($resp.user -and $resp.user.id) { $resp.user.id } else { $null }
    $userRole = if ($resp.role) { $resp.role } elseif ($resp.user -and $resp.user.role) { $resp.user.role } else { "" }
    if ($token) {
        Write-Host "  PASS [200] POST /api/auth/login  (role: $userRole userId: $userId)" -ForegroundColor Green; $pass++
    } else {
        Write-Host "  FAIL [200] No token in response" -ForegroundColor Red; $fail++
    }
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "  FAIL [$code] POST /api/auth/login" -ForegroundColor Red; $fail++
    Write-Host "`nSummary: PASS=$pass  FAIL=$fail  WARN=$warn"; exit 1
}

Write-Host "`n=== 2. AUTH GUARD - no token must be blocked ===" -ForegroundColor Cyan
T GET  "$LabBase/api/labs/templates"      401 -noAuth
T GET  "$LabBase/api/labs/my-lab"         401 -noAuth
T GET  "$LabBase/api/labs/admin/active"   401 -noAuth

Write-Host "`n=== 3. LAB TEMPLATES ===" -ForegroundColor Cyan
$tr = T GET "$LabBase/api/labs/templates" 200
$templateId = $null
if ($tr) {
    try { $list = $tr.Content | ConvertFrom-Json; if ($list -is [array] -and $list.Count -gt 0) { $templateId = $list[0].id; Write-Host "       First template id: $templateId" -ForegroundColor Gray } } catch {}
}

$newTpl = '{"name":"Test Lab Template","dockerImage":"ubuntu:22.04","description":"Automated test","cpuLimit":0.5,"memoryLimit":268435456}'
$cr = T POST "$LabBase/api/labs/templates" 201 $newTpl "(create)"
$createdTemplateId = $null
if ($cr) {
    try { $createdTemplateId = ($cr.Content | ConvertFrom-Json).id; Write-Host "       Created template id: $createdTemplateId" -ForegroundColor Gray } catch {}
}

# NOTE: No PUT /api/labs/templates/{id} endpoint exists in the controller — update skipped

Write-Host "`n=== 4. STUDENT - My Lab (unauthenticated blocked, tested again post-assign) ===" -ForegroundColor Cyan
# my-lab check is done below in section 4b, after the lab is assigned

Write-Host "`n=== 5. ASSIGN LAB ===" -ForegroundColor Cyan
if ($createdTemplateId) { $tplId = $createdTemplateId } else { $tplId = $templateId }
$assignedLabId = $null
if ($tplId) {
    $sid = if ($userId) { $userId } else { "00000000-0000-0000-0000-000000000001" }
    $assignBody = '{"studentId":"' + $sid + '","templateId":' + $tplId + '}'
    $ar = T POST "$LabBase/api/labs/assign" 201 $assignBody "(assign)"
    if ($ar) { try { $assignedLabId = ($ar.Content | ConvertFrom-Json).id; Write-Host "       Assigned lab id: $assignedLabId" -ForegroundColor Gray } catch {} }
} else {
    Write-Host "  SKIP - no template id available" -ForegroundColor Yellow; $warn++
}

Write-Host "`n=== 4b. STUDENT - My Lab (post-assign) ===" -ForegroundColor Cyan
if ($assignedLabId) {
    T GET "$LabBase/api/labs/my-lab" 200 -label "(may 404 if no running container for this user)"
} else {
    Write-Host "  SKIP - no assigned lab id" -ForegroundColor Yellow; $warn++
}

Write-Host "`n=== 6. COURSE-LAB CONFIGURE ===" -ForegroundColor Cyan
if ($tplId) { $cfgTpl = $tplId } else { $cfgTpl = 1 }
$cfgBody = '{"courseId":1,"templateId":' + $cfgTpl + '}'
T POST "$LabBase/api/labs/courses/configure" 201 $cfgBody "(configure)"

Write-Host "`n=== 7. LAB APPROVALS ===" -ForegroundColor Cyan
$approvalRes = T GET "$LabBase/api/labs/admin/approvals" 200 -label "(list pending)"
$approvalId = $null
if ($approvalRes) {
    try { $approvals = $approvalRes.Content | ConvertFrom-Json; if ($approvals -is [array] -and $approvals.Count -gt 0) { $approvalId = $approvals[0].id; Write-Host "       First approval id: $approvalId" -ForegroundColor Gray } } catch {}
}
if ($approvalId) {
    T POST "$LabBase/api/labs/admin/approvals/$approvalId/decide" 200 '{"approved":true}'  "(approve #$approvalId)"
    T POST "$LabBase/api/labs/admin/approvals/$approvalId/decide" 200 '{"approved":false,"rejectionReason":"test rejection"}' "(reject  #$approvalId)"
} else {
    Write-Host "  SKIP - no pending approvals" -ForegroundColor Yellow; $warn++
}

Write-Host "`n=== 8. ADMIN - Active Containers ===" -ForegroundColor Cyan
T GET "$LabBase/api/labs/admin/active" 200

Write-Host "`n=== 9. STOP LAB ===" -ForegroundColor Cyan
if ($assignedLabId) {
    T POST "$LabBase/api/labs/$assignedLabId/stop" 200 "" "(stop #$assignedLabId)"
} else {
    Write-Host "  SKIP - no assigned lab id" -ForegroundColor Yellow; $warn++
}

Write-Host "`n=== 10. CLEANUP ===" -ForegroundColor Cyan
# NOTE: No DELETE /api/labs/templates/{id} endpoint in controller — cleanup skipped
Write-Host "  SKIP - no delete endpoint on lab templates (manual cleanup needed if test template was created)" -ForegroundColor Yellow; $warn++

Write-Host "`n$('-'*55)" -ForegroundColor DarkGray
$color = if ($fail -gt 0) { "Red" } elseif ($warn -gt 0) { "Yellow" } else { "Green" }
Write-Host "  PASS=$pass   FAIL=$fail   WARN/SKIP=$warn" -ForegroundColor $color
Write-Host "$('-'*55)`n" -ForegroundColor DarkGray