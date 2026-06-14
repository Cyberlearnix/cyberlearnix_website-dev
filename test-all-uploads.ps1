param(
    [string]$Base = "https://prod-apis.cyberlearnix.com",
    [string]$Email = "shivakumar@cyberlearnix.com",
    [string]$Password = 'Shivam$179'
)

$ErrorActionPreference = "Stop"

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "         CYBERLEARNIX MEDIA UPLOAD TEST SUITE          " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $Base" -ForegroundColor Gray

# ── 1. Retrieve or Refresh Token ──────────────────────────────────────────────
$tokenFile = "$PSScriptRoot\token.txt"
$Token = $null

if (Test-Path $tokenFile) {
    $Token = (Get-Content $tokenFile -Raw).Trim()
}

# Try logging in to get a fresh token if missing/invalid
if (-not $Token) {
    Write-Host "`n[Auth] Token not found in token.txt. Logging in as $Email..." -ForegroundColor Gray
    try {
        $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
        $loginResp = Invoke-RestMethod -Uri "$Base/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
        $Token = $loginResp.token
        $Token | Set-Content $tokenFile
        Write-Host "       Login successful! Role: $($loginResp.user.role)" -ForegroundColor Green
    } catch {
        Write-Host "       Login failed: $_" -ForegroundColor Red
        Write-Host "       Please ensure your credentials in the script parameters are correct." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n[Auth] Using existing token from token.txt" -ForegroundColor Gray
}

# Helper to verify token validity
try {
    $profile = Invoke-RestMethod -Uri "$Base/api/users/profile" -Method GET -Headers @{ Authorization = "Bearer $Token" }
    Write-Host "       Token verified. Logged in as: $($profile.email) ($($profile.role))" -ForegroundColor Green
} catch {
    Write-Host "       Saved token is expired/invalid. Re-authenticating..." -ForegroundColor Yellow
    try {
        $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
        $loginResp = Invoke-RestMethod -Uri "$Base/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
        $Token = $loginResp.token
        $Token | Set-Content $tokenFile
        Write-Host "       Re-login successful! Role: $($loginResp.user.role)" -ForegroundColor Green
    } catch {
        Write-Host "       Re-login failed: $_" -ForegroundColor Red
        exit 1
    }
}

# ── 2. Define Test Helpers ───────────────────────────────────────────────────
$passCount = 0
$failCount = 0

# Base64 data for test assets
$tinyJpeg = [Convert]::FromBase64String("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwF/AD//2Q==")
$dummyPdf  = [System.Text.Encoding]::UTF8.GetBytes("%PDF-1.4`n1 0 obj`n<<`n/Type /Catalog`n>>`nendobj`ntrailer`n<<`n/Root 1 0 R`n>>`n%%EOF")
$dummyMp4  = [System.Text.Encoding]::UTF8.GetBytes("ftypmp42`nmoov`ntrak`nmdia`nminf`nstbl`nsdtp")

function Test-Upload {
    param(
        [string]$Path,
        [byte[]]$FileBytes,
        [string]$FileName,
        [string]$MimeType,
        [string]$Label,
        [int]$ExpectedStatus = 200,
        [hashtable]$AdditionalHeaders = @{}
    )

    Write-Host "`n-------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "Testing: $Label" -ForegroundColor Cyan
    Write-Host "Endpoint: POST $Path" -ForegroundColor DarkCyan

    $boundary = [System.Guid]::NewGuid().ToString()
    $LF = "`r`n"
    
    # We must decode the binary data to a string carefully or construct the byte array.
    # To support binary files safely in PowerShell 5.1 without encoding corruption, we construct the request body as bytes.
    $headerTemplate = "--$boundary$LF" +
                      "Content-Disposition: form-data; name=`"file`"; filename=`"$FileName`"$LF" +
                      "Content-Type: $MimeType$LF$LF"
                      
    $footerTemplate = "$LF--$boundary--$LF"
    
    $headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerTemplate)
    $footerBytes = [System.Text.Encoding]::UTF8.GetBytes($footerTemplate)
    
    # Combine bytes
    $requestBytes = New-Object byte[] ($headerBytes.Length + $FileBytes.Length + $footerBytes.Length)
    [System.Buffer]::BlockCopy($headerBytes, 0, $requestBytes, 0, $headerBytes.Length)
    [System.Buffer]::BlockCopy($FileBytes, 0, $requestBytes, $headerBytes.Length, $FileBytes.Length)
    [System.Buffer]::BlockCopy($footerBytes, 0, $requestBytes, ($headerBytes.Length + $FileBytes.Length), $footerBytes.Length)

    $headers = @{
        Authorization = "Bearer $Token"
    }
    foreach ($key in $AdditionalHeaders.Keys) {
        $headers[$key] = $AdditionalHeaders[$key]
    }

    try {
        $res = Invoke-WebRequest -Uri "$Base$Path" -Method POST -Headers $headers `
               -ContentType "multipart/form-data; boundary=$boundary" `
               -Body $requestBytes -UseBasicParsing -TimeoutSec 30
               
        if ($res.StatusCode -eq $ExpectedStatus) {
            Write-Host "PASS: Received expected status code $($res.StatusCode)" -ForegroundColor Green
            $script:passCount++
        } else {
            Write-Host "WARN: Received status code $($res.StatusCode) (expected $ExpectedStatus)" -ForegroundColor Yellow
        }
        
        if ($res.Content) {
            $json = $res.Content | ConvertFrom-Json
            Write-Host "Response Body: $($res.Content)" -ForegroundColor Gray
        }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $body = $sr.ReadToEnd()
            
            if ($code -eq $ExpectedStatus) {
                Write-Host "PASS: Received expected error code $code" -ForegroundColor Green
                $script:passCount++
            } else {
                Write-Host "FAIL: Received status code $code (expected $ExpectedStatus)" -ForegroundColor Red
                $script:failCount++
            }
            Write-Host "Response Body: $body" -ForegroundColor Gray
        } else {
            Write-Host "FAIL: Connection failed - $($_.Exception.Message)" -ForegroundColor Red
            $script:failCount++
        }
    }
}

# ── 3. Run Upload Tests ───────────────────────────────────────────────────────

# 1. Profile Photo (Image)
Test-Upload -Path "/api/users/upload/photo" `
            -FileBytes $tinyJpeg `
            -FileName "profile.jpg" `
            -MimeType "image/jpeg" `
            -Label "User Profile Photo Upload"

# 2. CMS Media Upload (Image)
Test-Upload -Path "/api/cms/media/upload?type=image" `
            -FileBytes $tinyJpeg `
            -FileName "cms-image.jpg" `
            -MimeType "image/jpeg" `
            -Label "CMS Media Upload (Image)" `
            -ExpectedStatus 201

# 3. Materials Thumbnail (Image)
Test-Upload -Path "/api/materials/upload/thumbnail" `
            -FileBytes $tinyJpeg `
            -FileName "thumbnail.jpg" `
            -MimeType "image/jpeg" `
            -Label "Course Thumbnail Upload (Materials API)"

# 4. Materials Drive Thumbnail (Image Alias)
Test-Upload -Path "/api/materials/drive/upload/thumbnail" `
            -FileBytes $tinyJpeg `
            -FileName "thumbnail-drive.jpg" `
            -MimeType "image/jpeg" `
            -Label "Course Thumbnail Upload (Drive Alias)"

# 5. Materials Module Image (Image)
Test-Upload -Path "/api/materials/upload/module-image" `
            -FileBytes $tinyJpeg `
            -FileName "module.jpg" `
            -MimeType "image/jpeg" `
            -Label "Chapter Module Image Upload"

# 6. Materials Banner (Image)
Test-Upload -Path "/api/materials/upload/banner" `
            -FileBytes $tinyJpeg `
            -FileName "banner.jpg" `
            -MimeType "image/jpeg" `
            -Label "Course Banner Image Upload"

# 7. Materials Video (Video)
Test-Upload -Path "/api/materials/upload/video" `
            -FileBytes $dummyMp4 `
            -FileName "lecture.mp4" `
            -MimeType "video/mp4" `
            -Label "Lecture Video Upload"

# 8. Materials Drive Video (Video Alias)
Test-Upload -Path "/api/materials/drive/upload/video" `
            -FileBytes $dummyMp4 `
            -FileName "lecture-drive.mp4" `
            -MimeType "video/mp4" `
            -Label "Lecture Video Upload (Drive Alias)"

# 9. Materials Document (Document)
Test-Upload -Path "/api/materials/upload/document" `
            -FileBytes $dummyPdf `
            -FileName "syllabus.pdf" `
            -MimeType "application/pdf" `
            -Label "Course Syllabus Document Upload"

# 10. Materials Drive Document (Document Alias)
Test-Upload -Path "/api/materials/drive/upload/document" `
            -FileBytes $dummyPdf `
            -FileName "syllabus-drive.pdf" `
            -MimeType "application/pdf" `
            -Label "Course Syllabus Document Upload (Drive Alias)"

# 11. Student Assignment Upload
Test-Upload -Path "/api/assignments/upload" `
            -FileBytes $dummyPdf `
            -FileName "submission.pdf" `
            -MimeType "application/pdf" `
            -Label "Student Assignment Submission Upload" `
            -AdditionalHeaders @{ "X-User-Id" = "cce6d990-6e2a-45b3-9f9e-3531ae232254" }

# ── 4. Print Test Summary ─────────────────────────────────────────────────────
Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "                     TEST SUMMARY                      " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "Total Passed: $passCount" -ForegroundColor Green
Write-Host "Total Failed: $failCount" -ForegroundColor $(if ($failCount -gt 0) {"Red"} else {"Green"})
Write-Host "=======================================================\n" -ForegroundColor Cyan
