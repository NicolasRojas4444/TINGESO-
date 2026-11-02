# SIGA IPT frontend

Functional initial interface: React, Vite, React Router, TypeScript, Tailwind CSS,
Axios and Keycloak. It uses real API responses, not startup/demo data.

## Start with Docker

From the repository root, configure the single `.env` as described in the root
README, then run:

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

## Frontend structure

```text
app/
  views/          # One view per page, grouped by admin/student/teacher/shared
  layouts/        # Workspace shell and role-protected areas
  components/     # Reusable UI, forms, layout, registration and grades
  service/        # API URLs and typed operations grouped by domain
  auth/           # Session context, roles and route protection
  config/         # Keycloak initialization and navigation menus
  hooks/          # Shared resource-loading hook
  lib/types.ts    # Shared API contracts
  utils/          # Form helpers
  styles/         # Theme, base, layout, components and page styles
  routes.ts       # Public navigation routes mapped to views
```

To change colors, edit **`app/styles/theme.css`**. Its semantic variables control
backgrounds, text, primary actions, borders and status messages. Layout rules are
in `styles/layout.css`, reusable UI styles in `styles/components.css`, and login
styles in `styles/views/login.css`. `app/app.css` imports these stylesheets.

Backend endpoint paths belong in **`app/service/*Service.ts`**, not in views or
components. `service/http.ts` centralizes Axios, bearer tokens and API errors;
`service/runtimeConfigService.ts` loads the public runtime configuration.

Keycloak is initialized once in **`app/config/keycloak.ts`**. Session state,
login/logout and role checks are separated into `app/auth/AuthProvider.tsx`,
`roles.ts` and `RequireRole.tsx`. Connection settings still come from `.env`
through `/config.json`; do not add credentials to these source files. Navigation
menus are configured in `app/config/navigation.ts`.

After editing, rebuild only the frontend without restarting its dependencies:

```powershell
docker compose up -d --build --no-deps --wait frontend
```

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

The official 72-course catalog and the selected `admin`, `teacher`, `student`
accounts can be prepared explicitly with the development-only
[data import tool](../docker/data-import/README.md). This is not run at startup.
The selected student's profile is linked to Analista Programador's plan 2021.3;
other students keep their existing plans unless changed explicitly by ADMIN.

Suggested walkthrough:

1. ADMIN creates a program, a current plan, courses and prerequisites.
2. ADMIN creates academic profiles linked to enabled Keycloak accounts.
3. ADMIN creates a period and sections with valid schedules and active teachers.
4. Supply the external enrollment, then STUDENT selects available courses.
5. TEACHER records final grades. ADMIN closes the period.
6. STUDENT consults consolidated history. Closed periods are read-only.

## Development

For containerized development with HMR, build the `dependencies` stage and run
`npm run dev -- --host 0.0.0.0` with the repository mounted and node_modules in a
Docker volume. Vite's `/config.json` reads public settings from the root `.env`.
The normal Compose service serves the compiled version; rebuild after UI edits.

The normal Compose service is the local development baseline. For the separate
server configuration with Nginx, HTTPS and three backend replicas, see
[deployment instructions](../docker/nginx/README.md). Tests, Docker Hub publication
and optional automatic server deployment are documented in the
[GitHub Actions setup](../.github/README.md).
