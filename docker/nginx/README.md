# Server startup with Nginx

Production runs one PostgreSQL, Keycloak, three backend replicas, one frontend
and Nginx. Only Nginx publishes host ports (80/443). Database and schema setup
containers run once per startup and exit; they are not extra serving replicas.

## Configure once on the server

Install Docker Engine with Compose 2.30 or newer and keep the complete repository
checkout up to date. In its single untracked `.env`, retain the real database and
Keycloak credentials and configure:

```dotenv
COMPOSE_FILE=compose.production.yaml
COMPOSE_PROJECT_NAME=siga-production
APP_PULL_POLICY=build
PUBLIC_URL=https://sigarojas.duckdns.org
LETSENCRYPT_DIRECTORY=/etc/letsencrypt
```

For manual source builds no published image tag is required. Remove example
`BACKEND_IMAGE`/`FRONTEND_IMAGE` values such as `sha-COMMIT_PUBLICADO`; local image
names are the defaults. Common required secrets are listed in the root README.
No second env file is used. An old `JPA_DDL_AUTO` setting is ignored by Compose.

The domain must point to the server and both its host/cloud firewall must allow
TCP 80/443. Keep the Certbot files on the server at:

- `/etc/letsencrypt/live/sigarojas.duckdns.org/fullchain.pem`
- `/etc/letsencrypt/live/sigarojas.duckdns.org/privkey.pem`

The entire Certbot directory is mounted read-only, preserving live/archive links.
Certificate issuance is not automated here. HTTPS is needed for browser PKCE.

## Start

From the repository root:

```sh
docker compose up --build -d
```

The command builds the applications for the server's architecture, then Compose
orders PostgreSQL, IAM database setup, Keycloak, its technical account setup, a
single application schema-update job, the three backend replicas, frontend and
Nginx. The schema job exits before the serving backends start with validation.
No manual SQL or initial single-backend command is needed.

IAM setup runs in its own root container rather than being sourced by PostgreSQL's
non-root entrypoint. It can read the mounted initialization script even if the
checkout has restrictive file permissions. Repeated setup creates only missing
database objects; it never resets existing passwords. Incorrect existing database
credentials must be corrected in `.env`, not worked around by deleting volumes.

No application users, enrollment or curriculum are seeded automatically. Existing
Keycloak clients retain their settings: after restoring data from another origin,
update `siga-frontend` redirect URIs to `PUBLIC_URL/*` and Web Origins to `PUBLIC_URL`.
Do not recreate identities to fix redirect URLs.

## Updates and data

Take a database backup before updates. The schema job uses Hibernate `update` for
normal additive schema changes, not a replacement for planned destructive data
migrations. Never use `down -v` to fix startup: it deletes the selected database.
Keep `COMPOSE_PROJECT_NAME` unchanged once deployed; different names select
different volumes and do not migrate data between them.

GitHub Actions uses the same Compose dependencies but supplies exact published
image tags and `APP_PULL_POLICY=always`. See [workflow setup](../../.github/README.md).
Its configuration-only upload does not update source code for later manual builds;
update your checkout first when building manually.

## Check

`docker compose ps -a` should show three healthy serving backends, healthy frontend,
PostgreSQL and Keycloak, running Nginx, and setup jobs exited with code 0. Detached
startup returning does not itself guarantee all services have become ready.

- `https://sigarojas.duckdns.org/`: application.
- `/config.json`: public API URL ends in `/api`, IAM URL ends in `/auth`.
- `/auth/realms/siga/.well-known/openid-configuration`: public issuer.
- Anonymous `/api/students`: 401, not frontend HTML.

Nginx sends `/api/` to the three backends by round robin, `/auth/` to Keycloak and
other paths to the frontend. `docker compose logs nginx` includes `upstream=` IPs
to verify the distribution. Docker DNS refresh handles recreated containers.

## Certificate renewal

After successful host Certbot renewal, check and reload Nginx:

```sh
docker compose exec -T nginx nginx -t && docker compose exec -T nginx nginx -s reload
```

Do not reload if the check fails. If Certbot uses standalone HTTP validation, it
cannot bind port 80 while Nginx owns it; retain a suitable renewal procedure. No
Certbot container, challenge route or cloud-resource automation is added here.
