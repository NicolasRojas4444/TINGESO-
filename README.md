# TINGESO-

SIGA IPT: Spring Boot backend and an initial functional React frontend.

Configure the root `.env` using `.env.example`, then start the local stack:

```powershell
docker compose up --build -d --wait
```

- Frontend: http://localhost:5173
- Backend / API: http://localhost:8080
- Keycloak: http://localhost:8081

Login is handled by Keycloak. The interface provides ADMIN, TEACHER and STUDENT
areas; it does not create demo accounts or enrollment on startup.

See [frontend/README.md](frontend/README.md) for account preparation, the academic
walkthrough and isolated browser tests. See [backend/API_NOTES.md](backend/API_NOTES.md)
for API contracts and account lifecycle details.
