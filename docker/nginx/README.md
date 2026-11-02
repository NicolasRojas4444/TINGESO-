# Nginx deployment

`compose.production.yaml` is a standalone server deployment, not an override for
the local Compose file. It runs one PostgreSQL, Keycloak, three backend replicas,
one frontend and one Nginx. Only Nginx publishes host ports (80/443).

Nginx routes `/api/` to the three backends using round robin, `/auth/` to Keycloak,
and everything else to the existing frontend server, including SPA deep links and
`/config.json`. Docker DNS refresh keeps routing valid after container recreation.
JWT Authorization headers and original paths/query strings are preserved.

## Prepare the server

1. Install Docker Engine with Compose **2.30 or newer**, and copy/clone the repo.
2. Point your domain's DNS at the server. Allow inbound TCP 80 and 443 in the cloud
   firewall; keep PostgreSQL, backend, frontend and Keycloak ports unpublished.
   Keep whatever SSH access you use to administer the server.
3. Supply a browser-trusted TLS certificate and private key for that domain on the
   server. Certificate issuance/renewal is not automated by this configuration.
   HTTPS is required for the frontend's PKCE browser cryptography outside localhost.
4. Copy `.env.production.example` to `.env.production`; fill in the real origin,
   Docker Hub image tags, secrets and absolute certificate paths. Do not use the
   local demo passwords. `PUBLIC_URL` must have no trailing slash or path.
5. Publish the frontend/backend images to your Docker Hub repositories before
   pulling them. This configuration does not publish images or set up a pipeline.

Run all commands from the repository root. Use the same `-p siga-production` each
time: the project name determines the persistent volume and network names.

```sh
docker compose --env-file .env.production -p siga-production -f compose.production.yaml config --quiet
docker compose --env-file .env.production -p siga-production -f compose.production.yaml pull
```

## First start with a fresh database

There is no schema-migration tool in the current application. Do not let three
Hibernate instances create/update the schema concurrently:

1. Temporarily set `JPA_DDL_AUTO=update` in `.env.production`.
2. Start just one backend; dependencies initialize PostgreSQL and Keycloak:

```sh
docker compose --env-file .env.production -p siga-production -f compose.production.yaml up -d --wait --scale backend=1 backend
```

3. Set `JPA_DDL_AUTO=validate` and start the full deployment:

```sh
docker compose --env-file .env.production -p siga-production -f compose.production.yaml up -d --wait
docker compose --env-file .env.production -p siga-production -f compose.production.yaml exec nginx nginx -t
docker compose --env-file .env.production -p siga-production -f compose.production.yaml ps
```

The final deployment must show **three** healthy backend containers, one frontend,
one Nginx, one PostgreSQL and one Keycloak. `keycloak-setup` is a one-off task that
should exit successfully. No test accounts, enrollment or catalog are seeded.

## Existing data and later deployments

Back up PostgreSQL before deploying changes. Do not run `down -v`: it deletes the
database volume. Starting this new project does **not** copy local data into the
cloud; restoring application and Keycloak data is a separate administrative step.

Keycloak's realm import only applies to a new realm. If restoring an existing
Keycloak database or changing the domain, edit client `siga-frontend` in its admin
console: Valid Redirect URIs = `PUBLIC_URL/*`, Web Origins = `PUBLIC_URL`, and clear
or update any old frontend/base URL pointing at localhost. Changing `.env.production`
alone does not rewrite an existing client's redirect configuration. Never recreate
users to work around a migration: application profiles depend on their subjects.

With an existing compatible schema, keep `JPA_DDL_AUTO=validate`:

```sh
docker compose --env-file .env.production -p siga-production -f compose.production.yaml pull
docker compose --env-file .env.production -p siga-production -f compose.production.yaml up -d --wait
```

Schema changes need a deliberate single-backend maintenance/update step and a
backup, as with initial setup; validation does not migrate a schema.

## Check from a browser

- `https://your-domain/`: application and Keycloak login/logout return correctly.
- `https://your-domain/config.json`: API URL is `PUBLIC_URL/api`, Keycloak URL is
  `PUBLIC_URL/auth`; no localhost addresses or secrets.
- `https://your-domain/auth/realms/siga/.well-known/openid-configuration`: issuer is
  `PUBLIC_URL/auth/realms/siga`.
- An anonymous `/api/students` request returns 401, proving the API route reaches
  the protected backend rather than the frontend's HTML fallback.

For load-balancing verification, make repeated API requests and inspect
`docker compose --env-file .env.production -p siga-production -f compose.production.yaml logs nginx`.
The `upstream=` field records the backend IP handling each request; compare the
three IPs with the backend containers on the Compose network. No debug endpoints
or public backend ports are added.

Swagger configuration, GitHub Actions, cloud resource creation, data migration
and certificate issuance remain separate tasks.
