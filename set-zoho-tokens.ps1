# set-zoho-tokens.ps1
# ─────────────────────────────────────────────────────────────────
# Injects Zoho Meeting OAuth credentials into the K3s cluster.
#
# HOW TO GET A NEW REFRESH TOKEN:
#   1. Go to https://api-console.zoho.in  (India DC)
#   2. Select your app → Self Client → Generate Code
#      Scope: ZohoMeeting.meeting.ALL,ZohoMeeting.webinar.READ
#      Expiry: 1 minute
#   3. Run the exchange call (replace <code> with the generated code):
#
#      curl -X POST "https://accounts.zoho.in/oauth/v2/token" \
#        -d "code=<code>" \
#        -d "client_id=1000.PGT6R2LA38ZQDQAJZBVVEO9UN5WAWD" \
#        -d "client_secret=<client_secret>" \
#        -d "redirect_uri=http://localhost" \
#        -d "grant_type=authorization_code"
#
#   4. Copy the "refresh_token" from the response JSON.
#   5. Enter it in the prompt below.
# ─────────────────────────────────────────────────────────────────

Clear-Host
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "       CYBERLEARNIX — ZOHO TOKEN INJECTOR        " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script patches the 'cyberlearnix-secrets' K8s secret"
Write-Host "with new Zoho OAuth credentials on BOTH namespaces."
Write-Host ""

$clientId     = Read-Host -Prompt "Zoho Client ID     (press Enter to keep default)"
$clientSecret = Read-Host -Prompt "Zoho Client Secret (press Enter to keep default)"
$refreshToken = Read-Host -Prompt "Zoho Refresh Token (REQUIRED)"

if (-not $refreshToken) {
    Write-Error "Refresh Token cannot be empty."
    exit 1
}

# Use defaults from application.properties if not provided
if (-not $clientId)     { $clientId     = "1000.PGT6R2LA38ZQDQAJZBVVEO9UN5WAWD" }
if (-not $clientSecret) {
    $clientSecret = Read-Host -Prompt "Zoho Client Secret is required"
    if (-not $clientSecret) { Write-Error "Client Secret cannot be empty."; exit 1 }
}

Write-Host "`nEncoding to Base64..." -ForegroundColor Cyan
$cidB64  = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientId.Trim()))
$csecB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientSecret.Trim()))
$rtB64   = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($refreshToken.Trim()))

$sshTarget = "root@145.223.22.177"
$sshPort   = "9022"

$patch = '{"data":{"zoho-client-id":"' + $cidB64 + '","zoho-client-secret":"' + $csecB64 + '","zoho-refresh-token":"' + $rtB64 + '"}}'

Write-Host "`nPatching dev namespace (cyberlearnix)..." -ForegroundColor Cyan
$cmdDev = "kubectl patch secret cyberlearnix-secrets -n cyberlearnix -p '" + $patch.Replace('"', '\"') + "'"
ssh -p $sshPort $sshTarget $cmdDev

Write-Host "`nPatching prod namespace (cyberlearnix-production)..." -ForegroundColor Cyan
$cmdProd = "kubectl patch secret cyberlearnix-secrets -n cyberlearnix-production -p '" + $patch.Replace('"', '\"') + "'"
ssh -p $sshPort $sshTarget $cmdProd

Write-Host "`nVerifying secret keys..." -ForegroundColor Cyan
ssh -p $sshPort $sshTarget "kubectl get secret cyberlearnix-secrets -n cyberlearnix -o json | jq -r '.data | keys[]' | grep zoho"
ssh -p $sshPort $sshTarget "kubectl get secret cyberlearnix-secrets -n cyberlearnix-production -o json | jq -r '.data | keys[]' | grep zoho"

Write-Host "`nRestarting admin-service to pick up new tokens..." -ForegroundColor Cyan
ssh -p $sshPort $sshTarget "kubectl rollout restart deployment/admin-service -n cyberlearnix"
ssh -p $sshPort $sshTarget "kubectl rollout restart deployment/admin-service -n cyberlearnix-production"

Write-Host "`n✅ Done! admin-service will load the new Zoho tokens on restart." -ForegroundColor Green
Write-Host "   Test: curl -H 'Authorization: Bearer <token>' https://prod-apis.cyberlearnix.com/api/admin/teams/meetings" -ForegroundColor Gray
