# Backend unit tests

From the repository root, with Docker Desktop running:

```powershell
docker build --progress plain -f backend/Dockerfile.test --output type=local,dest=backend/build/unit-test-artifacts backend
```

This executes `./gradlew clean check` inside a Java 21 container. No local Java,
running application, database, Keycloak or `.env` file is required. All repositories,
security contexts and provider HTTP responses are controlled in memory.

Reports:

- Coverage: `backend/build/unit-test-artifacts/reports/jacoco/test/html/index.html`
- Coverage XML: `backend/build/unit-test-artifacts/reports/jacoco/test/jacocoTestReport.xml`
- Tests: `backend/build/unit-test-artifacts/reports/tests/test/index.html`
- JUnit XML: `backend/build/unit-test-artifacts/test-results/test/`

JaCoCo measures **all classes under `siga.ipt.backend.services`**, including
registration and Keycloak provisioning. `check` fails if line coverage is below
90% for the service layer as a whole **or for an individual class**. No service
classes or business rules are excluded. Coverage is not a claim that all possible
business scenarios, concurrency or transaction behavior have been verified.

With a local Java 21 installation, `.\gradlew.bat check` is also available
when invoked from the `backend` directory.

The pre-existing `BackendApplicationTests.contextLoads` requires an actual Spring
application environment and is tagged `integration`; it is not counted as a unit
test. The default unit-test task excludes that tag. The assignment's separate
18 integration scenarios remain pending; these unit tests do not replace them.

Fixtures exist only in `src/test`. They never seed the running application or
write data to its database.
