# TINGESO-

SIGA IPT: Spring Boot backend and an initial functional React frontend.

Configure the root `.env` using `.env.example`, then start the local stack:

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

Login is handled by Keycloak. The interface provides ADMIN, TEACHER and STUDENT
areas; it does not create demo accounts or enrollment on startup.

See [frontend/README.md](frontend/README.md) for account preparation, the academic
walkthrough. See [backend/API_NOTES.md](backend/API_NOTES.md)
for API contracts and account lifecycle details.

See [backend/TESTING.md](backend/TESTING.md) for the Docker unit/coverage checks
and the isolated PostgreSQL integration suite (18 business scenarios).

For the standalone server configuration with Nginx, HTTPS and three backend
replicas, see [deployment instructions](docker/nginx/README.md).

To explicitly import the three official curricula from the assignment and prepare
the three selected local test accounts, see
[docker/data-import/README.md](docker/data-import/README.md). This is never run by
normal startup and does not erase existing users or academic records.
