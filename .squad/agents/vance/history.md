# Vance — Tech Lead History

## What I Know About This Project

### Architecture
- 10 Spring Boot microservices + 1 shared library
- Services: admin, cms, course, enrollment, form, gateway, instructor, notification, shop, user
- Build system: Gradle multi-module (settings.gradle declares all subprojects)
- Communication: REST APIs via gateway-service
- Data: PostgreSQL per-service or shared schema (confirm per-service)

### Conventions
- Each service has its own `Dockerfile` and `build.gradle`
- Source layout: `src/main/java/` + `src/main/resources/`
- Shared DTOs/models live in `shared-lib/`

### Decisions Made
- None recorded yet (first session)

### Open Questions
- Are services on separate DB schemas or a single PostgreSQL database?
- Is there a service-mesh or is all routing done at the gateway level?
- What auth mechanism is used? (JWT assumed from security docs)
