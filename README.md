# TINGESO-

SIGA IPT: Spring Boot backend and an initial functional React frontend.

Use a single untracked `.env` in the repository root on each machine. Keep your
existing passwords; do not commit or copy credentials into GitHub workflows.
Both development and production Compose read this same filename automatically.
After configuring `.env`, use the same startup command locally and on the server:

```powershell
docker compose up --build -d
```

There are only two Compose files:

- `compose.yaml`: development. Optional data import (`tools`) and isolated backend
  tests (`integration`) run only when explicitly requested, not on normal startup.
- `compose.production.yaml`: server deployment with Nginx and three backends.
  The server `.env` selects it using `COMPOSE_FILE` below.

Compose automatically creates the Keycloak database if missing and runs one
temporary backend to initialize/update the application schema before starting
the normal backend(s). These setup containers exit successfully; no manual SQL,
one-backend startup or changes between `update`/`validate` are required. Setup
seeds the official catalog and demo scenario only when `DEMO_SETUP_ENABLED=true`.

- Frontend: http://localhost:5173
- Backend / API: http://localhost:8080
- Keycloak: http://localhost:8081

## Environment configuration

Common required variables in `.env` are `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN`,
`KEYCLOAK_ADMIN_PASSWORD` and `KEYCLOAK_REGISTRATION_CLIENT_SECRET`.
Choose your own nonempty secrets; existing database and Keycloak credentials must
match their persisted accounts.

For development also set:

```dotenv
POSTGRES_PORT=5432
BACKEND_PORT=8080
KEYCLOAK_PORT=8081
FRONTEND_PORT=5173
FRONTEND_URL=http://localhost:5173
BACKEND_PUBLIC_URL=http://localhost:8080
KEYCLOAK_PUBLIC_URL=http://localhost:8081
CORS_ALLOWED_ORIGINS=http://localhost:5173
SELF_REGISTRATION_ENABLED=false
TEACHER_SELF_REGISTRATION_ENABLED=false
KEYCLOAK_REQUEST_TIMEOUT_SECONDS=10
DEMO_SETUP_ENABLED=true
DEMO_PASSWORD=tuki123
```

On the server, use the same `.env` filename and add:

```dotenv
PUBLIC_URL=https://sigarojas.duckdns.org
LETSENCRYPT_DIRECTORY=/etc/letsencrypt
COMPOSE_FILE=compose.production.yaml
COMPOSE_PROJECT_NAME=siga-production
APP_PULL_POLICY=build
DEMO_SETUP_ENABLED=true
DEMO_PASSWORD=tuki123
```

For manual source builds, `BACKEND_IMAGE`, `FRONTEND_IMAGE` and `DATA_IMPORT_IMAGE` are optional; remove
example values such as `sha-COMMIT_PUBLICADO`. Default local image names are used.
Actions supplies exact published image tags and overrides `APP_PULL_POLICY=always`
to use them without building on the server. Both paths use the same Compose setup
dependencies. `JPA_DDL_AUTO` in an old `.env` is no longer used by Compose: the
single schema job uses `update` and serving backends use `validate`.
Production derives public URLs from `PUBLIC_URL`, not the local URLs above.

Keep the same project name once deployed: changing it selects a different database
volume, not a copy of the existing data. Local development can omit `COMPOSE_FILE`,
`COMPOSE_PROJECT_NAME` and `APP_PULL_POLICY` to retain its current defaults.

GitHub Actions credentials are configured in repository **Secrets and variables**,
not in `.env` or `.secrets`. The integration runner generates temporary disposable
settings itself; no additional environment configuration file is needed.

Login is handled by Keycloak. The interface provides ADMIN, TEACHER and STUDENT
areas. Automatic demo accounts/enrollment require the explicit environment flag.

See [frontend/README.md](frontend/README.md) for account preparation, the academic
walkthrough. See [backend/API_NOTES.md](backend/API_NOTES.md)
for API contracts and account lifecycle details.
Swagger UI is available at `/api/swagger-ui.html` on the backend/public server;
see [API documentation usage](backend/API_NOTES.md#openapi--swagger).
ADMIN can create Keycloak accounts and academic profiles directly from
**Estudiantes → Nuevo estudiante** and **Docentes → Nuevo docente**.
Public registration stays disabled; the Compose startup command is unchanged.
The login screens display **SIGA IPT**; Keycloak remains the internal identity
provider, with the display name applied automatically to new and existing realms.
The optional [automatic demo initializer](docker/data-import/DEMO.md) prepares
`admin`, `teacher` and `student` (password from `DEMO_PASSWORD`) with all three
official study plans, 72 courses, 75 prerequisites, enrollment and seven sections
in period **2099-2**. Once initialized, restarts never reset passwords, catalog
edits, grades or progress. `down -v` deletes all data; the next startup rebuilds
only the initial demo when enabled, not the changes made during testing.

See [backend/TESTING.md](backend/TESTING.md) for the Docker unit/coverage checks
and the isolated PostgreSQL integration suite (18 business scenarios).

For the standalone server configuration with Nginx, HTTPS and three backend
replicas, see [deployment instructions](docker/nginx/README.md).
For tests, Docker Hub publication and automatic SSH deployment through GitHub
Actions (with manual deployment still available), see [workflow setup](.github/README.md).

The [legacy development importer](docker/data-import/README.md) is still available
for explicit catalog-only imports. Do not run its `all`/`accounts` modes on the
automatic demo: they prepare different profiles and may reset their passwords.
