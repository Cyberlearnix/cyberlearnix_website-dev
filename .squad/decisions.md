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

## Governance

- Vance reviews all cross-service and shared-lib changes
- Aria reviews all new endpoints for security coverage
- Quinn writes tests before a feature is marked done
- Kai owns all infrastructure changes
- All meaningful decisions are recorded here with an ADR number
