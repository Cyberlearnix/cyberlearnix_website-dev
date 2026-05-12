# Nova — Backend Engineer History

## What I Know About This Project

### Service Structure
Each microservice under `{service}-service/src/main/` follows the standard Spring Boot layout.
Shared types are in `shared-lib/`. All services declare `implementation project(':shared-lib')`.

### Build
- Root build: `./gradlew build`
- Single service: `./gradlew :{name}-service:build`
- Compiled classes go to `build/classes/`, JARs to `build/libs/`

### First Session
- No features implemented yet via Squad
- Baseline: project already has controllers, entities, and service layers in place
