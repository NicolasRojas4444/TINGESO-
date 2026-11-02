# Backend tests

## Unit tests

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

The default unit-test task excludes the `integration` tag. Integration tests run
separately using the standalone stack below; they do not inflate unit coverage.

Fixtures exist only in `src/test`. They never seed the running application or
write data to its database.

## Integration tests

From the repository root, with Docker Desktop running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File backend/scripts/test-integration.ps1
```

This builds a Java 21 test runner and starts a dedicated PostgreSQL 17.6 container.
The script generates an ephemeral database password in a temporary env file,
returns the test exit code, and removes the test containers/network even on
failure. PostgreSQL uses tmpfs (no persistent academic data), exposes no host port,
and never shares the normal Compose network or database volume. The test-only
Gradle cache volume may remain to speed up later runs.

Tests use the optional `integration` services in the development `compose.yaml`,
under a separate project named `siga-integration-tests`. The runner targets only
`integration-tests` and its test database dependency, never the application stack.
Do not run `up` without that service name in the test project.
The temporary env file is built from `.env.example` with disposable passwords;
it is deleted on exit and prevents Compose from loading the application `.env`.
No running backend or Keycloak is needed. Database URL checks
run before Hibernate initializes its test schema; fixtures additionally verify
the database name before clearing data between cases. Do not run the suite against
an application database.

The `integrationTest` Gradle task runs the `integration` tag, including the existing
context-load smoke test. There are **18 business cases** (three successes and three
rejections for each of three processes), plus that smoke test:

| Process | Successful scenarios | Rejection scenarios |
|---|---|---|
| Registration | Register three courses and occupy seats; replace selection retaining unchanged grades; cancel a fourth course releasing its seat and provisional grade | Missing enrollment; full section; overlapping replacement with original selection/grades preserved |
| Final grades | Record passing boundary 4.0; record failing 3.9; update an existing grade to 7.0 | Another teacher; out-of-range grade; update after actual period closing |
| Academic closing | Consolidate immutable snapshots while keeping REGULAR; graduate after all courses pass; eliminate after a second failure while retaining history | Missing grades with incomplete section IDs; non-ADMIN caller; repeated closing without duplicated history |

All cases use real Spring controllers, exception handling, security filters,
services, JPA repositories, transactions and PostgreSQL. Requests use MockMvc,
not a real HTTP socket. Tests do not wrap requests in a test transaction, so
post-request SQL assertions check committed results and rejected-operation
atomicity.

Authentication uses short-lived RSA-signed test JWTs with Keycloak-shaped realm
roles. A test-only decoder verifies signature, issuer and token lifetime. This
suite **does not** test Keycloak login, provisioning or frontend/browser behavior;
it does not mock academic services or repositories.

Reports (preserved even after test failures):

- HTML: `backend/build/integration-test-artifacts/reports/tests/integrationTest/index.html`
- JUnit XML: `backend/build/integration-test-artifacts/test-results/integrationTest/`

For a Linux shell or a future CI runner, use disposable variables. Application
password variables only satisfy development Compose interpolation; no application
containers are started by the following targeted command:

```sh
export INTEGRATION_DB_PASSWORD="$(openssl rand -hex 32)"
export POSTGRES_PASSWORD="$INTEGRATION_DB_PASSWORD"
export KEYCLOAK_DB_PASSWORD="$INTEGRATION_DB_PASSWORD"
export KEYCLOAK_ADMIN_PASSWORD="$INTEGRATION_DB_PASSWORD"
export KEYCLOAK_REGISTRATION_CLIENT_SECRET="$INTEGRATION_DB_PASSWORD"
docker compose --env-file .env.example -p siga-integration-tests -f compose.yaml up --build --abort-on-container-exit --exit-code-from integration-tests integration-tests
docker compose --env-file .env.example -p siga-integration-tests -f compose.yaml down
```

Always execute teardown and preserve the first command's exit code. Run both
unit and integration suites before publishing images; `check` intentionally
remains the unit/coverage gate and `integrationTest` is the separate database gate.
