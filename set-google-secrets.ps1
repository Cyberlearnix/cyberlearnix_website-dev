# set-google-secrets.ps1
# Interactive script to securely set Personal Google Drive credentials
# on the remote Kubernetes cluster (both dev and prod namespaces).

# Clear host and show high quality banner
Clear-Host
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "        CYBERLEARNIX GOOGLE CREDENTIALS INJECTOR         " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "This script will securely collect your personal Google API"
Write-Host "credentials, encode them, and inject them into the remote"
Write-Host "K3s Kubernetes cluster for both dev & prod namespaces."
Write-Host ""

# Request credentials interactively without sending them to the LLM context
$clientId = Read-Host -Prompt "Enter Google OAuth Client ID"
if (-not $clientId) {
    Write-Error "Client ID cannot be empty."
    exit 1
}

$clientSecret = Read-Host -Prompt "Enter Google OAuth Client Secret"
if (-not $clientSecret) {
    Write-Error "Client Secret cannot be empty."
    exit 1
}

$refreshToken = Read-Host -Prompt "Enter Google OAuth Refresh Token"
if (-not $refreshToken) {
    Write-Error "Refresh Token cannot be empty."
    exit 1
}

Write-Host "`nEncoding secrets to Base64..." -ForegroundColor Cyan
$clientIdB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientId.Trim()))
$clientSecretB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($clientSecret.Trim()))
$refreshTokenB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($refreshToken.Trim()))

# Ensure SSH connection details
$sshTarget = "root@145.223.22.177"
$sshPort = "9022"

Write-Host "Connecting to remote VM and patching Kubernetes Secrets on dev namespace (cyberlearnix)..." -ForegroundColor Cyan
$jsonDev = '{"data":{"google-drive-client-id":"' + $clientIdB64 + '","google-drive-client-secret":"' + $clientSecretB64 + '","google-drive-refresh-token":"' + $refreshTokenB64 + '"}}'
$cmdDev = "kubectl patch secret cyberlearnix-secrets -n cyberlearnix -p '" + $jsonDev.Replace('"', '\"') + "'"
ssh -p $sshPort $sshTarget $cmdDev

Write-Host "`nConnecting to remote VM and patching Kubernetes Secrets on prod namespace (cyberlearnix-production)..." -ForegroundColor Cyan
$jsonProd = '{"data":{"google-drive-client-id":"' + $clientIdB64 + '","google-drive-client-secret":"' + $clientSecretB64 + '","google-drive-refresh-token":"' + $refreshTokenB64 + '"}}'
$cmdProd = "kubectl patch secret cyberlearnix-secrets -n cyberlearnix-production -p '" + $jsonProd.Replace('"', '\"') + "'"
ssh -p $sshPort $sshTarget $cmdProd

Write-Host "`nValidating secret keys are injected..." -ForegroundColor Cyan
ssh -p $sshPort $sshTarget "echo 'Dev Environment Secrets:'; kubectl get secret cyberlearnix-secrets -n cyberlearnix -o json | jq -r '.data | keys'"
ssh -p $sshPort $sshTarget "echo 'Prod Environment Secrets:'; kubectl get secret cyberlearnix-secrets -n cyberlearnix-production -o json | jq -r '.data | keys'"

Write-Host "`nTriggering rollout restart for course-service and cms-service..." -ForegroundColor Cyan
ssh -p $sshPort $sshTarget "kubectl rollout restart deployment/course-service deployment/cms-service -n cyberlearnix"
ssh -p $sshPort $sshTarget "kubectl rollout restart deployment/course-service deployment/cms-service -n cyberlearnix-production"

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "                CONFIGURATION COMPLETED SUCCESSFULY        " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Your services are rolling out with the updated settings."
Write-Host "Run 'kubectl get pods -n cyberlearnix-production' on the shell to track rollout status."
Write-Host ""
