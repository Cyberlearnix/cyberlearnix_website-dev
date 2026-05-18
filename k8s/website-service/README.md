# website-service

Node.js/Express service (server.js, port 8082) handling Google Drive image uploads,
OAuth callbacks, and all primary API routes (courses, enrollments, banners, partners,
progress, health). Sits alongside Java microservices in the `cyberlearnix` namespace.

## Deployment Modes

**Mode 1 — Kubernetes on VPS (production)**
Build image on VPS, import into k3s containerd, apply manifests.
See `DEPLOYMENT.md → Website Service (K8s Deployment)`.

**Mode 2 — Docker Compose (local dev/staging)**
Add `website-service` block to `docker-compose.yml`, map port 8082.

**Mode 3 — Direct Node (development)**
`node server.js` — requires local `.env` with all secrets.

## Env Vars
All secrets loaded from Kubernetes Secret `website-service-secrets`.
See `DEPLOYMENT.md` for the full list and `kubectl create secret` command.

## Ingress
`ingress-patch.yaml` registers specific paths to this service.
The generic `/api` catch-all in gateway-service's ingress remains untouched.
