# Automatic official demo data (local and server)

Both Compose configurations run `demo-bootstrap` after schema initialization and
before the application backends. It seeds only when explicitly enabled and not
already completed. Passwords belong only to Keycloak, never application tables.

## Accounts and scenario

| Username | Role |
|---|---|
| `admin` | ADMIN |
| `teacher` | TEACHER |
| `student` | STUDENT |

The initializer imports the exact Appendix A catalog (72 courses, 75 prerequisite
links, four semesters per plan):

| Program | Current plan |
|---|---|
| 10450 - Técnico Analista Programador | 2021.3 |
| 10451 - Técnico en Análisis de Datos | 2022.3 |
| 10452 - Técnico en Ciberseguridad | 2021.3 |

The administrator needs no student/teacher profile. The student starts REGULAR in
the official Analista Programador plan; the teacher starts ACTIVE. Period `2099-2`
contains the six first-semester TAP courses and TAP201, which requires TAP101.
Seven sections have capacity 30 and 16 non-overlapping schedule blocks matching
the official TEL. The student is enrolled externally in that period. No course
registrations, grades or academic history are fabricated.

## Enable once in each machine's existing .env

Keep all existing database/IAM credentials and add:

```dotenv
DEMO_SETUP_ENABLED=true
DEMO_PASSWORD=tuki123
```

Use the normal command locally and on the server:

```sh
docker compose up --build -d
```

By default `DEMO_SETUP_ENABLED=false`; then the initializer exits without opening
a database connection or changing IAM data. `tuki123` is a deliberately weak demo
password requested for testing. Use a strong value or disable demo accounts for
real production. Normal application registration retains its password rules.

The initializer stores completion in `app_seed_state`. Subsequent startups skip
seeding entirely: no duplicated rows, password resets, reverted catalog edits,
new enrollment or reopened periods. Changing DEMO_PASSWORD later does not reset
existing accounts. With `down` alone, all data/progress persists. With `down -v`,
all database data is lost; the next `up --build -d` reconstructs the initial demo,
not any user-created registrations, grades or history. Keep backups for those.

Use the same password for the three demo accounts. If credentials are recorded in
`.demo-credentials`, that file is ignored by Git and must not be published. Keep
the server file owner-readable only (`chmod 600 .demo-credentials`). Demo accounts
have real privileges, including ADMIN: disable them after the demonstration.

## Try the workflow

1. ADMIN: consult the three official programs/plans, period `2099-2` and sections.
2. STUDENT: select `2099-2` and register 3–6 first-semester courses. The advanced
   course is rejected until its prerequisite is passed; cancellation/replacement
   still obey minimum-load and earlier-semester rules.
3. TEACHER: consult the assigned sections and record final grades (1.0–7.0).
4. ADMIN: attempt closing with missing grades to see rejection, then complete all
   grades and close. STUDENT can consult the resulting history.

Closing is definitive. To continue studying, ADMIN creates the next period and
offering, and external enrollment must be supplied as in the normal application.
The tool never reopens periods, fabricates history, or resets progress on rerun.

## Safety

Use backups before populating an existing deployment and avoid concurrent
administration during the first load. Conflicting official catalog data, reserved
identities or period `2099-2` abort initialization rather than overwrite user work.
Existing demo identities are accepted only when subject, RUN, email and role match.
Their credentials are never reset. Unrelated accounts are never adopted.

The previous synthetic `DEMO-SIGA` scenario is not deleted. Its selected student
may migrate to the official TAP plan only when REGULAR and without registrations
or history; otherwise the original plan/progress is retained and a warning is
logged. Existing enrollment is preserved. After the initial successful seed,
even manual changes to the demo data are left untouched on startup.

Database inserts are transactional. Newly created IAM accounts start disabled;
on failure the tool attempts to delete only those accounts. A provider outage can
prevent compensation; the error identifies any account requiring manual cleanup.
A database completion marker is saved in the same transaction as the scenario.
Catalog import uses its own transaction and may remain after a subsequent account
conflict; retry after resolving that conflict. A session advisory lock serializes
initializers. No application business endpoints or authentication are bypassed
during normal use; external enrollment is supplied only by this setup tool.

The short usernames are reserved for this scenario. Do not combine the legacy
local importer's `accounts` command with this demo: it prepares different profiles.
Existing conflicting accounts are never renamed automatically by the loader.

## Tests

The Docker build runs the Node unit tests. The following runner uses unique
disposable projects, not the root `.env` or main volumes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File docker/data-import/test-startup.ps1
```

It checks both Compose configurations, real role logins/academic workflows,
preservation on restart, and recreation after deleting only its test volumes.
CI runs the development variant with `-DevelopmentOnly`. Integration fixture
scripts are restricted to `siga_official_demo_test`; never target real data.

The old explicit `demo-data` command and `demo-setup.mjs` remain legacy tools for
the synthetic scenario; normal startup uses `bootstrap.mjs` and the official plans.
