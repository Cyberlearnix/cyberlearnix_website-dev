# Quinn — QA & Test Engineer History

## What I Know About This Project

### Test Infrastructure
- Build system: Gradle (run tests with `./gradlew test`)
- Test sources belong in `{service}/src/test/java/`
- Postman collections maintained in `docs/postman/`
- Python scripts for collection generation: `generate_collection.py`, `generate_collection_v2.py`

### Known Coverage Gaps
- No `src/test/` directories observed yet in any service (first Squad session)
- All services appear to have only `src/main/` — test infrastructure needs to be bootstrapped

### First Session
- No tests written yet via Squad
- Baseline: Postman collections for API-level testing exist
