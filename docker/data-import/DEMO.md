# Explicit demo data (local and server)

This optional tool works with either existing Compose configuration. It never
runs during `docker compose up --build -d`, changes existing accounts/passwords,
or resets academic progress. It is separate from the development-only official
catalog importer. Passwords are stored only in Keycloak, not application tables.

## Accounts and scenario

| Username | Role |
|---|---|
| `admin` | ADMIN |
| `teacher` | TEACHER |
| `student` | STUDENT |

The administrator needs no student/teacher profile. The demo student starts
REGULAR in `DEMO-SIGA`, current plan `DEMO-1`. The teacher starts ACTIVE. The
scenario contains seven explicitly named demo courses, an OPEN period `2099-1`,
seven sections with capacity 30 and valid non-overlapping schedules, and external
enrollment for the student. Six courses belong to semester 1; the seventh belongs
to semester 2 and requires passing the first course. No registrations, grades or
history are prefilled.

## Load explicitly

Start the application normally. Choose a strong password of at least 12 characters
and pass it only for this command; do not commit it. Existing root `.env`
database/Keycloak administration credentials are used without modification.

PowerShell:

```powershell
$env:DEMO_PASSWORD = '<chosen-test-password>'
docker compose run --build --rm --no-deps -e DEMO_SETUP_ENABLED=true -e DEMO_PASSWORD demo-data
Remove-Item Env:DEMO_PASSWORD
```

Server (Bash):

```bash
read -rs -p 'Demo password: ' DEMO_PASSWORD; echo
export DEMO_PASSWORD
docker compose run --build --rm --no-deps -e DEMO_SETUP_ENABLED=true -e DEMO_PASSWORD demo-data
unset DEMO_PASSWORD
```

Use the same password for the three demo accounts. If credentials are recorded in
`.demo-credentials`, that file is ignored by Git and must not be published. Keep
the server file owner-readable only (`chmod 600 .demo-credentials`). Demo accounts
have real privileges, including ADMIN: disable them after the demonstration.

## Try the workflow

1. ADMIN: consult the demo program/plan/courses, period `2099-1` and sections.
2. STUDENT: select `2099-1` and register 3–6 first-semester courses. The advanced
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
academic administration during the short load. `DEMO-SIGA`, period `2099-1`,
the three usernames, emails and RUNs must be unused for a first load. Conflicts
stop the operation instead of adopting identities or overwriting data.

Database inserts are transactional. Newly created IAM accounts start disabled;
on failure the tool attempts to delete only those accounts. A provider outage can
prevent compensation; the error identifies any account requiring manual cleanup.
A completed rerun validates the demo identities and leaves passwords and progress
unchanged. Edited/incomplete demo data may require manual review; it is not reset.

The short usernames are reserved for this scenario. Do not combine the legacy
local importer's `accounts` command with this demo: it prepares different profiles.
Existing conflicting accounts are never renamed automatically by the loader.

The Docker build runs the unit tests. `tests/demo-integration.mjs` is restricted
to a disposable empty database (`ISOLATED_DEMO_TEST=true`); never run it against
local or server data.
