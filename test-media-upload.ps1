param([string]$Base = "https://apis.cyberlearnix.com")

$token = (Get-Content "$PSScriptRoot\token.txt" -Raw -Encoding UTF8).Trim()

# Minimal valid JPEG
$tmpFile = "$env:TEMP\test_thumb.jpg"
[System.IO.File]::WriteAllBytes($tmpFile, [byte[]](0xFF,0xD8,0xFF,0xE0,0,16,0x4A,0x46,0x49,0x46,0,1,1,0,0,1,0,1,0,0,0xFF,0xD9))

function Req([string]$label, [string]$uri, [hashtable]$hdrs) {
    if (-not $hdrs) { $hdrs = @{} }
    $code = 0; $body = ""
    try {
        $r = Invoke-RestMethod -Uri $uri -Method POST -Headers $hdrs -Form @{ file = Get-Item $tmpFile } -TimeoutSec 30
        Write-Host "PASS [$label] 200 $($r | ConvertTo-Json -Compress)" -ForegroundColor Green
        return
    } catch { $code = [int]$_.Exception.Response.StatusCode.value__; $ex = $_ }
    $resp = $ex.Exception.Response
    if ($resp) { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $body = $sr.ReadToEnd() }
    if ($code -in 401,403) { Write-Host "PASS [$label] $code (auth blocked)" -ForegroundColor Green }
    elseif ($code -eq 503) { Write-Host "FAIL [$label] 503 Drive not enabled — $body" -ForegroundColor Red }
    else { Write-Host "INFO [$label] $code — $body" -ForegroundColor Yellow }
}

Write-Host "`n=== MEDIA UPLOAD TESTS → $Base ===" -ForegroundColor Cyan

# 1. Health
try {
    $h = Invoke-RestMethod -Uri "$Base/actuator/health" -TimeoutSec 10
    Write-Host "Gateway health: $($h.status)" -ForegroundColor Green
} catch { Write-Host "Gateway health FAIL: $($_.Exception.Message)" -ForegroundColor Red }

Req "thumbnail NO-AUTH"       "$Base/api/materials/upload/thumbnail"   $null
Req "thumbnail WITH-TOKEN"    "$Base/api/materials/upload/thumbnail"   @{Authorization="Bearer $token"}
Req "cms/media WITH-TOKEN"    "$Base/api/cms/media/upload"             @{Authorization="Bearer $token"}
Req "photo WITH-TOKEN"        "$Base/api/users/upload/photo"           @{Authorization="Bearer $token"}

Write-Host "`nDone." -ForegroundColor Cyan
