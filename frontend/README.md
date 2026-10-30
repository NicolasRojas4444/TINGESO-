# SIGA IPT frontend

Functional initial interface: React, Vite, React Router, TypeScript, Tailwind CSS,
Axios and Keycloak. It uses real API responses, not startup/demo data.

## Start with Docker

From the repository root, configure `.env` using `.env.example`, then run:

```powershell
docker compose up --build -d --wait
```

Open `http://localhost:5173`. The existing Keycloak client is `siga-frontend`.
Login uses the Keycloak page and authorization code flow with PKCE S256. Tokens
stay in memory; Axios refreshes them before requests. No application passwords,
client secrets or tokens are stored in localStorage/sessionStorage.

The frontend Docker build runs TypeScript checks, 16 component/unit tests and
two HTTP runtime tests before building the SPA. The final image runs as a
non-root user and has no npm production dependencies.

The root `.env` configures the public URLs. Defaults are frontend port 5173,
backend port 8080 and Keycloak port 8081. Keep `FRONTEND_URL`,
`CORS_ALLOWED_ORIGINS` and the exposed frontend port consistent. Set
`BACKEND_PUBLIC_URL` if the browser reaches the API through a different origin.
Only the public API URL, Keycloak URL, realm and public client ID are exposed at
`/config.json`; credentials are never copied into the frontend image.

## Views

- ADMIN: students, student detail/history/status/plan changes, teachers and their
  sections, programs, plans, courses/prerequisites, periods, sections/schedules,
  roster and read-only grades, and definitive period closing.
- TEACHER: assigned sections, period filter, roster, final-grade creation/update
  per student while the period is open, and closed-period consultation.
- STUDENT: own profile, curriculum, initial registration, atomic full-selection
  replacement, individual cancellation and consolidated history.

Routes and menu options are role protected. Accounts with multiple roles can
switch areas from the sidebar. Backend authorization remains authoritative.
Failures are shown without losing the submitted form or desired registration
selection. Missing-grade closing failures display the structured section IDs.

Course data and prerequisites are saved in two explicit steps: saving a course
opens its prerequisite form. A prerequisite failure does not imply that the
previous course save was rolled back.

## Accounts and enrollment for manual testing

Create ADMIN manually in Keycloak and assign the ADMIN realm role. To create an
academic profile from ADMIN, first create the Keycloak account, enable it, assign
STUDENT or TEACHER, and copy its user ID into the profile form. Administering
passwords/roles is intentionally not part of this frontend.

Alternatively, the backend's temporary public registration endpoint can create
STUDENT/TEACHER accounts while its environment flags permit it. A public sign-up
screen and account recovery/relinking screens are not included in this version.

Enrollment is external to SIGA IPT. A STUDENT must have a corresponding existing
`enrollments` row to save a registration. Creating a profile does not enroll it.
The UI displays the real backend rejection if enrollment is missing; it never
bypasses this rule or adds fake enrollment automatically.

Suggested walkthrough:

1. ADMIN creates a program, a current plan, courses and prerequisites.
2. ADMIN creates academic profiles linked to enabled Keycloak accounts.
3. ADMIN creates a period and sections with valid schedules and active teachers.
4. Supply the external enrollment, then STUDENT selects available courses.
5. TEACHER records final grades. ADMIN closes the period.
6. STUDENT consults consolidated history. Closed periods are read-only.

## Reproducible browser tests (Windows / Docker Desktop)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File frontend/scripts/test-e2e.ps1
```

The runner starts a uniquely named disposable Compose project, uses Chromium
against the real Keycloak/PostgreSQL/backend/frontend, and removes **only its own
volumes** afterwards. Test users, clients, courses and enrollment exist only in
that isolated database. The main project's data is never seeded or removed.
Ports 15432, 18081, 18080 and 15173 must be available.

Browser tests cover actual login, ADMIN catalog changes, missing enrollment,
STUDENT registration/cancellation/replacement and route denial, structured
incomplete closing, TEACHER grade creation/update, ADMIN read-only grades,
successful closing, consolidated STUDENT history and logout. They do not replace
the backend's 18 formal integration cases required by the assignment.

Failure screenshots/traces are saved under ignored `frontend/test-results`.
Treat traces as sensitive: they can contain authenticated network requests.

For containerized development with HMR, build the `dependencies` stage and run
`npm run dev -- --host 0.0.0.0` with the repository mounted and node_modules in a
Docker volume. Vite's `/config.json` reads public settings from the root `.env`.
The normal Compose service serves the compiled version; rebuild after UI edits.

This is a functional development baseline, not the final production deployment
with Nginx, three backend replicas, HTTPS, CD and Docker Hub publication.
