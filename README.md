# TINGESO-

SIGA IPT: Spring Boot backend and an initial functional React frontend.

Use a single untracked `.env` in the repository root on each machine. Keep your
existing passwords; do not commit or copy credentials into GitHub workflows.
Both development and production Compose read this same filename automatically.
To start locally:

```powershell
docker compose up --build -d --wait
```

There are only two Compose files:

- `compose.yaml`: development. Optional data import (`tools`) and isolated backend
  tests (`integration`) run only when explicitly requested, not on normal startup.
- `compose.production.yaml`: server deployment with Nginx and three backends.

- Frontend: http://localhost:5173
- Backend / API: http://localhost:8080
- Keycloak: http://localhost:8081

## Environment configuration

Common required variables in `.env` are `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN`,
`KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_REGISTRATION_CLIENT_SECRET` and `JPA_DDL_AUTO`.
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
```

On the server, use the same `.env` filename and add:

```dotenv
PUBLIC_URL=https://sigarojas.duckdns.org
LETSENCRYPT_DIRECTORY=/etc/letsencrypt
```

Set `BACKEND_IMAGE` and `FRONTEND_IMAGE` to real published Docker Hub tags from the
same successful workflow commit. Do not put example labels like
`sha-COMMIT_PUBLICADO` in those variables. Set `JPA_DDL_AUTO=validate` after initial
schema setup (see deployment instructions). Production does not use the local
ports/URLs above; it derives public URLs from `PUBLIC_URL`.

GitHub Actions credentials are configured in repository **Secrets and variables**,
not in `.env` or `.secrets`. The integration runner generates temporary disposable
settings itself; no additional environment configuration file is needed.

Login is handled by Keycloak. The interface provides ADMIN, TEACHER and STUDENT
areas; it does not create demo accounts or enrollment on startup.

See [frontend/README.md](frontend/README.md) for account preparation, the academic
walkthrough. See [backend/API_NOTES.md](backend/API_NOTES.md)
for API contracts and account lifecycle details.

See [backend/TESTING.md](backend/TESTING.md) for the Docker unit/coverage checks
and the isolated PostgreSQL integration suite (18 business scenarios).

For the standalone server configuration with Nginx, HTTPS and three backend
replicas, see [deployment instructions](docker/nginx/README.md).
For tests, Docker Hub publication and automatic SSH deployment through GitHub
Actions (with manual deployment still available), see [workflow setup](.github/README.md).

To explicitly import the three official curricula from the assignment and prepare
the three selected local test accounts, see
[docker/data-import/README.md](docker/data-import/README.md). This is never run by
normal startup and does not erase existing users or academic records.
