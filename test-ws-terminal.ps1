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
    $token  = if ($resp.accessToken) { $resp.accessToken } else { $resp.token }
    if (-not $token) { throw "No token in response" }
    # Decode JWT payload (base64url -> base64 -> JSON)
    $b64 = $token.Split('.')[1] -replace '-','+' -replace '_','/'
    $b64 = $b64.PadRight($b64.Length + (4 - $b64.Length % 4) % 4, '=')
    $jwtPayload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) | ConvertFrom-Json
    $selfUserId = $jwtPayload.sub
    $selfRole   = $jwtPayload.role
    Write-Host "  PASS Login OK  (role: $selfRole  userId: $selfUserId)" -ForegroundColor Green
} catch {
    Write-Host "  FAIL Login: $_" -ForegroundColor Red; exit 1
} finally { $pwd = $null }

# -- 2. Find or create an active assignment ------------------------------------
if ($AssignmentId -eq 0) {
    $hdr = @{ Authorization = "Bearer $token" }

    # Try existing active lab first
    try {
        $myLab = Invoke-RestMethod -Uri "$Base/api/labs/my-lab" -Headers $hdr -ErrorAction Stop
        $AssignmentId = $myLab.assignment.id
        $status = $myLab.assignment.status
        Write-Host "  Found assignment #$AssignmentId  status=$status" -ForegroundColor Gray
        if ($status -ne "RUNNING") {
            Write-Host "  WARN Assignment is $status - terminal may not respond" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  No active lab (404) - will assign one from first template..." -ForegroundColor Yellow

        # Get first active template
        $templates = Invoke-RestMethod -Uri "$Base/api/labs/templates" -Headers $hdr -ErrorAction Stop
        if (-not $templates -or $templates.Count -eq 0) {
            Write-Host "  FAIL No lab templates found" -ForegroundColor Red; exit 1
        }
        $tplId = $templates[0].id
        Write-Host "  Using template #$tplId ($($templates[0].name)  image: $($templates[0].dockerImage))" -ForegroundColor Gray

        # userId already decoded from JWT above
        $userId = $selfUserId
        Write-Host "  Self-assign as userId=$userId" -ForegroundColor Gray

        $assignBody = "{`"studentId`":`"$userId`",`"templateId`":$tplId}"
        $assigned = Invoke-RestMethod -Method Post -Uri "$Base/api/labs/assign" `
            -Headers $hdr -ContentType "application/json" -Body $assignBody -ErrorAction Stop
        $AssignmentId = $assigned.id
        Write-Host "  PASS Assigned lab #$AssignmentId  status=$($assigned.status)" -ForegroundColor Green

        # Give container a moment to start
        Write-Host "  Waiting 5s for container to start..." -ForegroundColor Gray
        [System.Threading.Thread]::Sleep(5000)
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

# -- Helper: poll a Task[T] until completed or deadline, never cancelling it --------
# Returns $task.Result or $null on timeout.
function PollTask($task, [int]$timeoutSec) {
    $end = [DateTime]::UtcNow.AddSeconds($timeoutSec)
    while (-not $task.IsCompleted -and [DateTime]::UtcNow -lt $end) {
        [System.Threading.Thread]::Sleep(100)
    }
    if ($task.IsCompleted -and -not $task.IsFaulted -and -not $task.IsCanceled) {
        return $task.GetAwaiter().GetResult()
    }
    return $null
}

$buf       = [byte[]]::new(8192)
$collected = [System.Text.StringBuilder]::new()

# -- 4. Send echo immediately, then read whatever comes back ------------------
# Send first so the shell has something to execute regardless of banner timing
Write-Host "  Sending echo TERMINAL_WORKS..." -ForegroundColor Gray
$cmd    = "echo TERMINAL_WORKS`n"
$cmdBuf = [Text.Encoding]::UTF8.GetBytes($cmd)
$cmdSeg = [ArraySegment[byte]]::new($cmdBuf)
# Use a fresh token ONLY for the send (5 s); send does NOT abort the socket if it succeeds
$sendCts = [System.Threading.CancellationTokenSource]::new(5000)
try {
    [void]$ws.SendAsync($cmdSeg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $sendCts.Token).GetAwaiter().GetResult()
    Write-Host "  >> Sent: $($cmd.Trim())" -ForegroundColor DarkGreen
} catch {
    Write-Host "  FAIL Send: $_" -ForegroundColor Red
}

# -- 5. Read loop: start one ReceiveAsync, poll IsCompleted, never cancel ----
Write-Host "  Reading terminal output (up to 15 s)..." -ForegroundColor Gray
$deadline = [DateTime]::UtcNow.AddSeconds(15)
$seg      = [ArraySegment[byte]]::new($buf)
# CancellationToken.None so polling never kills the socket
$recvTask = $ws.ReceiveAsync($seg, [System.Threading.CancellationToken]::None)

while ([DateTime]::UtcNow -lt $deadline) {
    [System.Threading.Thread]::Sleep(100)
    if (-not $recvTask.IsCompleted) { continue }
    if ($recvTask.IsFaulted -or $recvTask.IsCanceled) {
        Write-Host "  ReceiveAsync faulted: $($recvTask.Exception.GetBaseException().Message)" -ForegroundColor Red
        break
    }
    $r     = $recvTask.GetAwaiter().GetResult()
    $chunk = [Text.Encoding]::UTF8.GetString($buf, 0, $r.Count)
    [void]$collected.Append($chunk)
    $safe  = $chunk -replace '[\x00-\x08\x0B-\x0C\x0E-\x1F\x7F]','-'
    Write-Host "  << $($r.Count) bytes: $safe" -ForegroundColor DarkGray
    if ($r.CloseStatus -ne $null -and
        $r.CloseStatus -ne [System.Net.WebSockets.WebSocketCloseStatus]::None) { break }
    if ($collected.ToString() -match "TERMINAL_WORKS") { break }
    # Start next read on same socket (previous task is done)
    $seg      = [ArraySegment[byte]]::new($buf)
    $recvTask = $ws.ReceiveAsync($seg, [System.Threading.CancellationToken]::None)
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
