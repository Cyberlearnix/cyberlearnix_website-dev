param()
$password = "Shivam" + [char]36 + "179"
$body = '{"email":"shivakumar@cyberlearnix.com","password":"' + $password + '"}'
Write-Host "Testing with body: $body"

try {
    $resp = Invoke-RestMethod -Method Post -Uri "https://apis.cyberlearnix.com/api/auth/login" `
        -ContentType "application/json" -Body $body -ErrorAction Stop
    Write-Host "Login OK. Role: $($resp.role)"
    $token = $resp.accessToken
    
    # Test GET /api/notifications/inbox
    $headers = @{ "Authorization" = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Method Get -Uri "https://apis.cyberlearnix.com/api/notifications/inbox" `
            -Headers $headers -ErrorAction Stop
        Write-Host "GET inbox: $($r.StatusCode) $($r.Content)"
    } catch {
        $code = $_.Exception.Response.StatusCode.Value__
        Write-Host "GET inbox FAILED: $code"
        try { 
            $errBody = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream()).ReadToEnd()
            Write-Host "Error body: $errBody"
        } catch {}
    }
    
    # Test POST /api/notifications/inbox/admin/send
    $sendBody = '{"type":"ANNOUNCEMENT","title":"Test","body":"Hello","targetRole":"STUDENT"}'
    try {
        $r2 = Invoke-WebRequest -Method Post `
            -Uri "https://apis.cyberlearnix.com/api/notifications/inbox/admin/send" `
            -Headers $headers -ContentType "application/json" -Body $sendBody -ErrorAction Stop
        Write-Host "POST admin/send: $($r2.StatusCode) $($r2.Content)"
    } catch {
        $code = $_.Exception.Response.StatusCode.Value__
        Write-Host "POST admin/send FAILED: $code"
    }
} catch {
    Write-Host "Login FAILED: $($_.Exception.Message)"
}
