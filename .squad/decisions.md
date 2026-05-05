# Squad Decisions

## Active Decisions

### [2026-04-21] ADR-001: Squad Team Composition for Cyberlearnix
**Decision:** Established a 5-member specialist team (Vance, Nova, Aria, Kai, Quinn) plus Scribe and Ralph.
**Rationale:** The platform is a Java Spring Boot microservices system requiring dedicated ownership of backend, security, DevOps, and QA domains. A generalist team would create context collisions across 10 services.
**Consequences:** Each agent owns a clear domain; cross-domain work requires explicit handoff.
**Status:** Accepted

### [2026-04-21] ADR-002: Tech Stack Baseline
**Decision:** Java Spring Boot 3.x, Gradle multi-module, PostgreSQL, Docker Compose.
**Rationale:** This is the existing production stack. Squad agents will follow these constraints.
**Consequences:** No new framework introductions without Vance sign-off.
**Status:** Accepted

### [2026-04-21] ADR-003: Azure AKS + Helm Deployment Architecture
**Decision:** Deploy all 10 microservices to Azure Kubernetes Service using a single generic Helm chart with per-service values. Use Azure OIDC for GitHub Actions authentication (no stored credentials).
**Rationale:** AKS provides managed Kubernetes with native Azure integrations (ACR pull via managed identity, Key Vault CSI). Single Helm chart with `range` iteration is DRY and maintainable for 10 homogeneous Spring Boot services. OIDC eliminates long-lived service principal secrets in GitHub.
**Consequences:** All services must expose Spring Actuator health endpoints at `/actuator/health/liveness` and `/actuator/health/readiness`.
**Status:** Accepted

### [2026-04-21] ADR-004: Zero-downtime Deployment Strategy
**Decision:** RollingUpdate with `maxUnavailable: 0`, PodDisruptionBudgets with `minAvailable: 1`, and topology spread constraints across 3 AZs.
**Rationale:** The platform is live. No downtime is acceptable during deployments or cluster maintenance.
**Status:** Accepted

### [2026-04-21] ADR-005: Secrets Management
**Decision:** Secrets (DB password, JWT secret, Redis password) stored in Azure Key Vault AND synced to GitHub Secrets. Kubernetes Secrets created/updated by GitHub Actions deploy workflow. No secrets in images, ConfigMaps, or git.
**Status:** Accepted

### [2026-05-05] ADR-006: Production Cluster is K3s (Not AKS)
**Decision:** Acknowledge that the live production deployment runs on a single-node K3s cluster on VPS `145.223.22.177`, not Azure AKS.
**Rationale:** The Azure AKS architecture (ADR-003) is the target/future state. Current production uses K3s + ArgoCD + GHCR. All K8s operations must target the K3s cluster.
**Consequences:** Azure-specific tooling (ACR, Key Vault CSI, managed identity) not available. Node resources are constrained (2 CPU, 7.8GB RAM).
**Status:** Accepted

### [2026-05-05] ADR-007: BestEffort QoS for Monitoring Pods
**Decision:** All monitoring pods (Prometheus, Grafana, Loki, Promtail) are deployed with NO `resources:` spec (BestEffort QoS).
**Rationale:** The single K3s node has 2 CPUs 100% requested by the 10 application services. Any pod with a CPU request fails scheduling (`Insufficient cpu`). BestEffort pods have 0 CPU request and schedule without issue.
**Consequences:** Monitoring pods may be the first evicted under memory pressure. Acceptable — monitoring is non-critical vs. application workloads.
**Status:** Accepted

### [2026-05-05] ADR-008: Daily Automated Backup Strategy
**Decision:** PostgreSQL backups via `pg_dump` inside `postgres-0` pod, all 8 service DBs, daily at 1:00 AM, 7-day retention on `/var/backups/cyberlearnix-postgres/`.
**Rationale:** No cloud backup service available on bare-metal K3s. `kubectl exec` into the StatefulSet pod is the portable solution. 7-day retention balances recovery window vs. disk usage.
**Status:** Accepted

### [2026-05-05] ADR-009: Monitoring Stack — Prometheus + Grafana + Loki
**Decision:** Deploy Prometheus (metrics), Grafana (dashboards), Loki (logs), Promtail (log shipper) all in the `monitoring` namespace.
**Rationale:** Full observability stack needed — metrics via Spring Actuator `/actuator/prometheus`, logs via pod log scraping. Loki WAL disabled (permission issue with emptyDir).
**Access:** Grafana at `https://apis.cyberlearnix.com/grafana` (admin / CyberLearnix@2026), Prometheus at `/prometheus`.
**Status:** Accepted

## Governance

- Vance reviews all cross-service and shared-lib changes
- Aria reviews all new endpoints for security coverage
- Quinn writes tests before a feature is marked done
- Kai owns all infrastructure changes
- All meaningful decisions are recorded here with an ADR number
