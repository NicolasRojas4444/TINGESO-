# Explicit local data preparation

This is a one-off administrative import, not an application startup fixture,
Spring profile or replacement authentication mechanism. Normal Compose startup
does not run it. It never changes the master administrator or technical users.
It is included in the development `compose.yaml` under the optional `tools` profile;
explicitly targeting `academic-import` activates it without an extra Compose file.

## Catalog

`catalog.json` transcribes Appendix A of `Evaluacion1_v1.pdf` (pages 12–13):

| Code | Program | Current plan | Courses |
|---|---|---|---|
| 10450 | Técnico Analista Programador | 2021.3 | 24 |
| 10451 | Técnico en Análisis de Datos | 2022.3 | 24 |
| 10452 | Técnico en Ciberseguridad | 2021.3 | 24 |

Each plan has six courses per semester, four semesters, its exact TEL/SCT values
and the prerequisites specified in the PDF. Data is UTF-8.

Course rows are `[code, name, semester, theory, exercise, laboratory, sct,
prerequisiteCodes]`. Unit tests validate counts, hours, credits and prerequisite
ordering before the image can build.

## Run

Start the main stack from the repository root:

```powershell
docker compose up -d --wait
```

Set these in the untracked root `.env`:

```dotenv
LOCAL_DATA_SETUP_ENABLED=true
LOCAL_TEST_PASSWORD=<local-test-password>
```

Then run one of these **explicitly**:

```powershell
# Catalog plus the three selected test accounts/profiles:
docker compose run --build --rm --no-deps academic-import all

# Catalog only; no account/password changes:
docker compose run --build --rm --no-deps academic-import catalog

# Accounts only, after the official catalog is present:
docker compose run --build --rm --no-deps academic-import accounts
```

The master administrator credentials must match the **existing** Keycloak master
account, not simply a newly edited `.env`. Passwords are sent only to Keycloak.
They are never inserted into the application database or printed in logs.

## Selected local accounts

| Login in SIGA IPT | Role | Academic profile |
|---|---|---|
| admin | ADMIN | Local identity; no student/teacher profile required |
| teacher | TEACHER | Existing teacher preserved, or an ACTIVE profile created |
| student | STUDENT | Analista Programador, current plan 2021.3 |

All three receive `LOCAL_TEST_PASSWORD`, non-temporary, and are enabled. Existing
Keycloak sessions for those three are logged out; obtain a fresh login afterwards.
Previously issued access JWTs retain the backend's normal expiry/cutoff policy.

The separate existing `academic-admin` account is **not renamed or modified**:
Keycloak's username-editing policy remains unchanged. The simple `admin` account
uses its own local identity (`local-admin@example.test`) rather than reclaiming
the old administrator's email or RUN. Existing selected teacher/student subjects,
profiles, personal metadata and academic references are preserved. Missing
identities are never reclaimed by matching another account's email or RUN.
Suspended accounts or accounts with another academic role/profile require manual
review instead of automatic recovery or privilege removal.

Only the selected `student` may move to the official plan, and only if it has no
registrations or academic history, following the existing plan-change rule.
Enrollment alone is preserved and does not prevent that change. Other students
retain their plans, including students still linked to a demo plan. To move them,
use ADMIN → Students → detail → Change program/plan, when permitted by the backend.

No periods, sections, schedules, registrations, grades, history or enrollment are
created by this tool. After catalog import, use ADMIN to prepare the period's
offering. A student still needs external enrollment to register courses.

## Preservation and failure behavior

- The catalog is imported in one PostgreSQL transaction. Rerunning inserts only
  missing identical entries; it does not duplicate rows or overwrite edited data.
- Conflicting course data, prerequisite links or a different current official
  plan abort the catalog transaction. Existing unrelated programs/plans remain.
- All selected identities are preflighted before passwords are changed. Local
  profile changes are transactional; new Keycloak accounts start disabled, with
  best-effort deletion if profile persistence fails.
- Catalog import and account preparation are separate operations. Keycloak and
  PostgreSQL do not share a distributed transaction. Password finalization occurs
  after profile persistence and cannot restore unknown previous passwords. A
  provider failure may leave a partial setup; inspect progress and rerun after
  correcting the cause. No success message is printed for a failed account.
- Use only on the local development stack, without concurrent administration.
  Make backups before running it against existing data. Do not enable in production.
- A short local test password does **not** weaken public registration validation
  or global Keycloak password policy. If the realm rejects it, setup stops.

## Tests

The Docker image runs the dependency-free Node unit tests during build. To verify
real PostgreSQL/Keycloak/backend behavior without changing main data:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File docker/data-import/test-isolated.ps1
```

The runner uses a unique disposable project on ports 15432/18081/18080 and removes
only its own volumes. Its fixtures verify selected identity preservation, external
enrollment preservation, student plan migration, repeat imports, all three real
logins/API permissions, unchanged non-target credentials, conflicting catalog
rejection and rejection of plan changes after academic activity.
