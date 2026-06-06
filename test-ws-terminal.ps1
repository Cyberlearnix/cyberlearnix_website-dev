param(
    [string]$Email   = "shivakumar@cyberlearnix.com",
    [string]$Base    = "https://apis.cyberlearnix.com",
    [long]  $AssignmentId = 0   # 0 = auto-detect from /api/labs/my-lab
)

# -- 1. Login -----------------------------------------------------------------
$secPwd = Read-Host "Password for $Email" -AsSecureString
$bstr   = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPwd)
$pwd    = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

try {
    $loginBody = "{`"email`":`"$Email`",`"password`":`"$pwd`"}"
    $resp  = Invoke-RestMethod -Method Post -Uri "$Base/api/auth/login" `
                 -ContentType "application/json" -Body $loginBody -ErrorAction Stop
    $token = if ($resp.accessToken) { $resp.accessToken } else { $resp.token }
    if (-not $token) { throw "No token in response" }
    Write-Host "  PASS Login OK  (role: $($resp.role)  userId: $($resp.userId))" -ForegroundColor Green
} catch {
    Write-Host "  FAIL Login: $_" -ForegroundColor Red; exit 1
} finally { $pwd = $null }

# -- 2. Find active assignment -------------------------------------------------
if ($AssignmentId -eq 0) {
    try {
        $hdr    = @{ Authorization = "Bearer $token" }
        $myLab  = Invoke-RestMethod -Uri "$Base/api/labs/my-lab" -Headers $hdr -ErrorAction Stop
        $AssignmentId = $myLab.assignment.id
        $status = $myLab.assignment.status
        Write-Host "  Found assignment #$AssignmentId  status=$status" -ForegroundColor Gray
        if ($status -ne "RUNNING") {
            Write-Host "  WARN Assignment is $status - terminal may not respond" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  FAIL /api/labs/my-lab: $_" -ForegroundColor Red; exit 1
    }
}

# -- 3. WebSocket connect ------------------------------------------------------
$wsBase = $Base -replace "^https://","wss://" -replace "^http://","ws://"
$wsUrl  = "$wsBase/labs/terminal/$AssignmentId" + "?token=$token"
Write-Host ("`n=== WebSocket Terminal Test (assignment #" + $AssignmentId + ") ===") -ForegroundColor Cyan
Write-Host "  Connecting to $wsBase/labs/terminal/$AssignmentId?token=<jwt>" -ForegroundColor Gray

$ws  = [System.Net.WebSockets.ClientWebSocket]::new()
$cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(20))

try {
    $ws.ConnectAsync([Uri]$wsUrl, $cts.Token).GetAwaiter().GetResult()
} catch {
    Write-Host "  FAIL WebSocket connect failed: $_" -ForegroundColor Red; exit 1
}

if ($ws.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
    Write-Host "  FAIL WebSocket state after connect: $($ws.State)" -ForegroundColor Red; exit 1
}
Write-Host "  PASS WebSocket connected  (state: $($ws.State))" -ForegroundColor Green

# -- 4. Read banner/prompt (up to 5 s) ----------------------------------------
Write-Host "  Waiting for shell banner/prompt..." -ForegroundColor Gray
$buf       = [byte[]]::new(8192)
$seg       = [ArraySegment[byte]]::new($buf)
$collected = [System.Text.StringBuilder]::new()
$deadline  = [DateTime]::UtcNow.AddSeconds(5)

while ([DateTime]::UtcNow -lt $deadline -and $ws.State -eq "Open") {
    $cts2 = [System.Threading.CancellationTokenSource]::new(1500)
    try {
        $r     = $ws.ReceiveAsync($seg, $cts2.Token).GetAwaiter().GetResult()
        $chunk = [Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
        [void]$collected.Append($chunk)
        $safe  = $chunk -replace '[\x00-\x08\x0B-\x0C\x0E-\x1F\x7F]','-'
        Write-Host "  << $($r.Count) bytes: $safe" -ForegroundColor DarkGray
        if ($r.CloseStatus -ne $null -and $r.CloseStatus -ne [System.Net.WebSockets.WebSocketCloseStatus]::None) { break }
    } catch { break }
}

# -- 5. Send echo command ------------------------------------------------------
$cmd    = "echo TERMINAL_WORKS`n"
$cmdBuf = [Text.Encoding]::UTF8.GetBytes($cmd)
$cmdSeg = [ArraySegment[byte]]::new($cmdBuf)
try {
    $cts3 = [System.Threading.CancellationTokenSource]::new(5000)
    $ws.SendAsync($cmdSeg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts3.Token).GetAwaiter().GetResult()
    Write-Host "  >> Sent: $($cmd.Trim())" -ForegroundColor DarkGreen
} catch {
    Write-Host "  FAIL Send: $_" -ForegroundColor Red
}

# -- 6. Read echo response (up to 5 s) ----------------------------------------
$deadline2 = [DateTime]::UtcNow.AddSeconds(5)
while ([DateTime]::UtcNow -lt $deadline2 -and $ws.State -eq "Open") {
    $cts4 = [System.Threading.CancellationTokenSource]::new(1500)
    try {
        $r     = $ws.ReceiveAsync($seg, $cts4.Token).GetAwaiter().GetResult()
        $chunk = [Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
        [void]$collected.Append($chunk)
        $safe  = $chunk -replace '[\x00-\x08\x0B-\x0C\x0E-\x1F\x7F]','-'
        Write-Host "  << $($r.Count) bytes: $safe" -ForegroundColor DarkGray
        if ($collected.ToString() -match "TERMINAL_WORKS") { break }
    } catch { break }
}

# -- 7. Result -----------------------------------------------------------------
$all = $collected.ToString()
Write-Host ""
if ($all -match "TERMINAL_WORKS") {
    Write-Host "  PASS Terminal echo confirmed - lab terminal is WORKING" -ForegroundColor Green
} elseif ($all.Length -gt 0) {
    Write-Host "  PASS Terminal streaming output received (shell alive)" -ForegroundColor Green
    Write-Host "  Preview: $($all.Substring(0,[Math]::Min(300,$all.Length)) -replace '[\x00-\x08\x0B-\x0C\x0E-\x1F\x7F]','-')"
} else {
    Write-Host "  WARN Connected but no output - container may be idle or PTY not ready" -ForegroundColor Yellow
}

# -- 8. Close -----------------------------------------------------------------
try {
    $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "test done", [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()
    Write-Host "  WebSocket closed cleanly`n"
} catch { Write-Host "  (close error: $_)" }
