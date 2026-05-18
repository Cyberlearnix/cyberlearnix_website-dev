# Cyberlearnix LMS - Automatic Deployment Setup

## Overview
This project automatically deploys to production when you push to the `main` branch.

## Required GitHub Secrets

You need to add these secrets to your GitHub repository:

1. **NETLIFY_AUTH_TOKEN** - Your Netlify personal access token
2. **NETLIFY_SITE_ID** - Your Netlify site ID

## Setup Steps

### Step 1: Get Netlify Auth Token
1. Go to https://app.netlify.com/user/applications/personal
2. Click "New access token"
3. Name it "Cyberlearnix LMS Deploy"
4. Copy the token

### Step 2: Get Netlify Site ID
**Option A - Create new site:**
1. Go to https://app.netlify.com/
2. Click "Add new site" → "Import an existing project"
3. Import from GitHub: `Cyberlearnix/frontendUI`
4. Build command: `npm run build`
5. Publish directory: `dist`
6. Site settings → General → Site details → Site ID

**Option B - Use existing site:**
1. Go to Site Settings → General → Site details
2. Copy the Site ID

### Step 3: Add GitHub Secrets
1. Go to your GitHub repository: `https://github.com/Cyberlearnix/frontendUI`
2. Settings → Secrets and variables → Actions
3. Click "New repository secret"
4. Add `NETLIFY_AUTH_TOKEN` (from Step 1)
5. Add `NETLIFY_SITE_ID` (from Step 2)

### Step 4: Configure Custom Domain
1. In Netlify: Site Settings → Domain Management
2. Add custom domain: `lms.cyberlearnix.com`
3. Update DNS to point to Netlify

## How It Works

When you push to `main`:
1. GitHub Actions triggers automatically
2. Builds the project: `npm run build`
3. Deploys to Netlify production
4. Updates `lms.cyberlearnix.com`

## Backend Configuration

The frontend is already configured to use:
- **Backend API**: `https://apis.cyberlearnix.com`

Updated in:
- `vite.config.js` - API proxy settings
- `netlify.toml` - Redirect rules

## Manual Deploy (if needed)

```bash
npm run build
netlify deploy --prod --dir=dist
```

## Files Changed for Deployment

- `.github/workflows/deploy.yml` - GitHub Actions workflow
- `netlify.toml` - Netlify configuration
- `vite.config.js` - Backend API endpoints

---

## Website Service (K8s Deployment)

`website-service` is a Node.js/Express pod (server.js, port 8082) running in the
`cyberlearnix` namespace alongside the Java microservices.

### Manifests
- `k8s/website-service/deployment.yaml` — Deployment (1 replica, imagePullPolicy: Never)
- `k8s/website-service/service.yaml` — ClusterIP on port 8082
- `k8s/website-service/ingress-patch.yaml` — Ingress with specific path rules

### Step-by-Step Deployment (PowerShell / Windows)

```powershell
$sshKey  = "C:\Users\palak\Downloads\ssh\.ssh\id_ed25519"
$vpsHost = "root@145.223.22.177"
$sshPort = "9022"

# 1. Create temp build directory on VPS
ssh -i $sshKey -p $sshPort $vpsHost "mkdir -p /tmp/website-service"

# 2. Copy source files to VPS
scp -i $sshKey -P $sshPort "server.js"         "${vpsHost}:/tmp/website-service/"
scp -i $sshKey -P $sshPort "package.json"       "${vpsHost}:/tmp/website-service/"
scp -i $sshKey -P $sshPort "package-lock.json"  "${vpsHost}:/tmp/website-service/"
scp -r -i $sshKey -P $sshPort "api/"            "${vpsHost}:/tmp/website-service/api/"
scp -i $sshKey -P $sshPort "Dockerfile"         "${vpsHost}:/tmp/website-service/"

# 3. Build Docker image on VPS
ssh -i $sshKey -p $sshPort $vpsHost "cd /tmp/website-service && docker build -t website-service:latest ."

# 4. Import into k3s containerd (skip if using plain Docker runtime)
ssh -i $sshKey -p $sshPort $vpsHost "docker save website-service:latest | k3s ctr images import - 2>/dev/null || echo 'Not k3s — image available via docker'"

# 5. Create Kubernetes secret (REPLACE placeholder values before running)
ssh -i $sshKey -p $sshPort $vpsHost @"
kubectl create secret generic website-service-secrets \
  -n cyberlearnix \
  --from-literal=GOOGLE_CLIENT_ID='708800913500-ueg75d1ctthq4uppuoeop0ot5cdbfed3.apps.googleusercontent.com' \
  --from-literal=GOOGLE_CLIENT_SECRET='REPLACE' \
  --from-literal=GOOGLE_REFRESH_TOKEN='REPLACE' \
  --from-literal=OAUTH_CALLBACK_BASE_URL='https://apis.cyberlearnix.com' \
  --from-literal=GOOGLE_DRIVE_ROOT_FOLDER_ID='1cbSVD2uMSI765X934VCA0iHSjNkZdb6z' \
  --from-literal=PAYU_MERCHANT_KEY='REPLACE' \
  --from-literal=PAYU_SALT='REPLACE' \
  --from-literal=PAYU_ENV='production' \
  --from-literal=GMAIL_USER='REPLACE' \
  --from-literal=GMAIL_APP_PASSWORD='REPLACE' \
  --from-literal=RESEND_API_KEY='REPLACE' \
  --dry-run=client -o yaml | kubectl apply -f -
"@

# 6. Copy K8s manifests to VPS and apply
scp -i $sshKey -P $sshPort "k8s/website-service/deployment.yaml"    "${vpsHost}:/tmp/website-service/"
scp -i $sshKey -P $sshPort "k8s/website-service/service.yaml"        "${vpsHost}:/tmp/website-service/"
scp -i $sshKey -P $sshPort "k8s/website-service/ingress-patch.yaml"  "${vpsHost}:/tmp/website-service/"

ssh -i $sshKey -p $sshPort $vpsHost "kubectl apply -f /tmp/website-service/deployment.yaml -f /tmp/website-service/service.yaml -f /tmp/website-service/ingress-patch.yaml"

# 7. Verify deployment
ssh -i $sshKey -p $sshPort $vpsHost "kubectl get pods -n cyberlearnix | grep website; kubectl get svc -n cyberlearnix | grep website; kubectl get ingress -n cyberlearnix"

# 8. Smoke test upload endpoint
Invoke-WebRequest -Uri "https://apis.cyberlearnix.com/api/health" -UseBasicParsing | Select-Object -ExpandProperty Content
```

### Rollback

```powershell
$sshKey  = "C:\Users\palak\Downloads\ssh\.ssh\id_ed25519"
$vpsHost = "root@145.223.22.177"
$sshPort = "9022"

ssh -i $sshKey -p $sshPort $vpsHost @"
kubectl delete deployment website-service -n cyberlearnix
kubectl delete svc website-service -n cyberlearnix
kubectl delete ingress website-service-ingress -n cyberlearnix
"@
```

### Ingress Routing Notes

| Path prefix | Target service |
|---|---|
| `/api/upload-image` | website-service:8082 |
| `/auth/google` | website-service:8082 |
| `/api/banners` | website-service:8082 |
| `/api/partners` | website-service:8082 |
| `/api/courses` | website-service:8082 |
| `/api/course-management` | website-service:8082 |
| `/api/enrollments` | website-service:8082 |
| `/api/progress` | website-service:8082 |
| `/api/health` | website-service:8082 |
| `/api` (catch-all) | gateway-service:8080 |
| `/` (catch-all) | gateway-service:8080 |

The specific paths above are registered in a **separate** Ingress resource
(`website-service-ingress`). The existing gateway-service Ingress retains `/api`
and `/` as catch-alls — nginx longest-prefix matching ensures the specific paths
win over the catch-all.
