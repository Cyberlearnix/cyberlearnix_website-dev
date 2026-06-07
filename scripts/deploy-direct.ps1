#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build a service locally and deploy directly to k3s (both dev + prod).
    Bypasses GitHub Actions CI entirely.

.DESCRIPTION
    1. Builds the Gradle fat jar
    2. Builds a Docker image tagged with current git SHA
    3. Imports the image directly into k3s containerd on the server (no GHCR push needed)
    4. Updates the deployment image in both namespaces via kubectl set image
    5. Waits for rollout in both namespaces

.PARAMETER Service
    Service name, e.g. gateway-service, user-service, course-service
    Use "all" to deploy every service (slow).

.PARAMETER Env
    Target environment: "both" (default), "dev", or "prod"

.PARAMETER Server
    SSH target host (default: 145.223.22.177)

.PARAMETER Port
    SSH port (default: 9022)

.PARAMETER NoGhcr
    If set, imports image directly into k3s containerd via SSH (no GHCR push needed).
    Default is to import directly — set this flag to push to GHCR instead.

.EXAMPLE
    # Deploy gateway-service to both envs
    .\scripts\deploy-direct.ps1 -Service gateway-service

.EXAMPLE
    # Deploy user-service to dev only
    .\scripts\deploy-direct.ps1 -Service user-service -Env dev

.EXAMPLE
    # Deploy all services to prod only
    .\scripts\deploy-direct.ps1 -Service all -Env prod
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$Service,

    [ValidateSet("both","dev","prod")]
    [string]$Env = "both",

    [string]$Server = "145.223.22.177",
    [int]$Port = 9022,
    [string]$Registry = "ghcr.io/cyberlearnix",

    [switch]$PushGhcr   # Use GHCR instead of direct import (requires docker login ghcr.io)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$DevNamespace  = "cyberlearnix"
$ProdNamespace = "cyberlearnix-production"
$SSH           = "ssh -p $Port root@$Server"

# ── 1. Determine services to build ──────────────────────────────────────────
$ALL_SERVICES = @(
    "gateway-service","user-service","course-service","enrollment-service",
    "notification-service","shop-service","form-service","admin-service",
    "cms-service","instructor-service","attendance-service","lab-service"
)
$services = if ($Service -eq "all") { $ALL_SERVICES } else { @($Service) }

# ── 2. Get SHA for image tag ──────────────────────────────────────────────────
$SHA = (git rev-parse --short=12 HEAD).Trim()
Write-Host "`n=== Direct Deploy | SHA: $SHA | Env: $Env ===" -ForegroundColor Cyan

foreach ($svc in $services) {
    $imageName  = "cyberlearnix-$svc"
    $imageTag   = "$Registry/${imageName}:$SHA-local"
    $imageLocal = "${imageName}:${SHA}-local"

    Write-Host "`n--- Building $svc ---" -ForegroundColor Yellow

    # ── 3. Gradle build ──────────────────────────────────────────────────────
    Write-Host "  [1/4] Gradle bootJar..."
    & ./gradlew ":${svc}:bootJar" -q --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed for $svc" }

    # ── 4. Docker build ──────────────────────────────────────────────────────
    Write-Host "  [2/4] Docker build -> $imageLocal..."
    docker build -t $imageLocal "./$svc" --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $svc" }

    if ($PushGhcr) {
        # ── Push to GHCR (requires: docker login ghcr.io -u <username>) ─────
        docker tag $imageLocal $imageTag
        Write-Host "  [3/4] Pushing to GHCR: $imageTag..."
        docker push $imageTag
        if ($LASTEXITCODE -ne 0) { throw "GHCR push failed for $svc" }
        $deployImage = $imageTag
    } else {
        # ── Direct import into k3s containerd via SSH ─────────────────────
        Write-Host "  [3/4] Importing image directly into k3s on $Server..."
        docker save $imageLocal | & ssh -p $Port root@$Server "k3s ctr images import -"
        if ($LASTEXITCODE -ne 0) { throw "Image import failed for $svc" }
        $deployImage = $imageLocal
    }

    # ── 5. kubectl set image ─────────────────────────────────────────────────
    Write-Host "  [4/4] Deploying $svc..."

    $containerName = $svc  # container name matches service name in templates

    if ($Env -eq "dev" -or $Env -eq "both") {
        Write-Host "        -> dev ($DevNamespace)..."
        & ssh -p $Port root@$Server "kubectl set image deployment/$svc ${containerName}=$deployImage -n $DevNamespace 2>&1"
    }

    if ($Env -eq "prod" -or $Env -eq "both") {
        Write-Host "        -> prod ($ProdNamespace)..."
        & ssh -p $Port root@$Server "kubectl set image deployment/$svc ${containerName}=$deployImage -n $ProdNamespace 2>&1"
    }
}

# ── 6. Wait for rollouts ─────────────────────────────────────────────────────
Write-Host "`n=== Waiting for rollouts ===" -ForegroundColor Cyan

foreach ($svc in $services) {
    if ($Env -eq "dev" -or $Env -eq "both") {
        Write-Host "  $svc (dev)..."
        & ssh -p $Port root@$Server "kubectl rollout status deployment/$svc -n $DevNamespace --timeout=120s 2>&1"
    }
    if ($Env -eq "prod" -or $Env -eq "both") {
        Write-Host "  $svc (prod)..."
        & ssh -p $Port root@$Server "kubectl rollout status deployment/$svc -n $ProdNamespace --timeout=120s 2>&1"
    }
}

# ── 7. Quick smoke test ───────────────────────────────────────────────────────
Write-Host "`n=== Smoke test ===" -ForegroundColor Cyan
if ($services -contains "gateway-service") {
    $devStatus  = & ssh -p $Port root@$Server "curl -sk -o /dev/null -w '%{http_code}' -X OPTIONS 'https://apis.cyberlearnix.com/api/auth/login' -H 'Origin: https://dev.cyberlearnix.com' -H 'Access-Control-Request-Method: POST'"
    $prodStatus = & ssh -p $Port root@$Server "curl -sk -o /dev/null -w '%{http_code}' -X OPTIONS 'https://prod-apis.cyberlearnix.com/api/auth/login' -H 'Origin: https://cyberlearnix.com' -H 'Access-Control-Request-Method: POST'"
    Write-Host "  CORS preflight dev:  $devStatus (expect 200)"  -ForegroundColor $(if ($devStatus -eq "200") {"Green"} else {"Red"})
    Write-Host "  CORS preflight prod: $prodStatus (expect 200)" -ForegroundColor $(if ($prodStatus -eq "200") {"Green"} else {"Red"})
}

Write-Host "`nDone." -ForegroundColor Green
