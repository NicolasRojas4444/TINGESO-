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
2. Point `sigarojas.duckdns.org` at the server. Allow inbound TCP 80 and 443 in the cloud
   firewall; keep PostgreSQL, backend, frontend and Keycloak ports unpublished.
   Keep whatever SSH access you use to administer the server.
3. Keep the Certbot certificate and private key on the Ubuntu server at
   `/etc/letsencrypt/live/sigarojas.duckdns.org/fullchain.pem` and
   `/etc/letsencrypt/live/sigarojas.duckdns.org/privkey.pem`.
   The whole `/etc/letsencrypt` directory is mounted read-only so links from
   `live/` to `archive/` remain valid. A missing directory fails startup rather
   than creating an empty host directory. Certificate issuance/renewal is not
   automated by this configuration.
   HTTPS is required for the frontend's PKCE browser cryptography outside localhost.
4. Configure the single `.env` in the repository root with your real
   Docker Hub image tags and secrets (common variables are listed in the root README). Keep
   `PUBLIC_URL=https://sigarojas.duckdns.org` and
   `LETSENCRYPT_DIRECTORY=/etc/letsencrypt`. Do not use the local demo passwords.
   Replace old `TLS_CERTIFICATE_FILE` and `TLS_PRIVATE_KEY_FILE` settings with
   `LETSENCRYPT_DIRECTORY`. A domain change also needs
   matching `server_name`, redirect and certificate paths in `default.conf`.
5. Publish the frontend/backend images before pulling them, or configure the
   [GitHub Actions workflow](../../.github/README.md), which tests, publishes and
   deploys them. Initial server/schema preparation is still required once.

Run all commands from the repository root. Use the same `-p siga-production` each
time: the project name determines the persistent volume and network names.

```sh
docker compose -p siga-production -f compose.production.yaml config --quiet
docker compose -p siga-production -f compose.production.yaml pull
```

## First start with a fresh database

There is no schema-migration tool in the current application. Do not let three
Hibernate instances create/update the schema concurrently:

1. Temporarily set `JPA_DDL_AUTO=update` in `.env`.
2. Start just one backend; dependencies initialize PostgreSQL and Keycloak:

```sh
docker compose -p siga-production -f compose.production.yaml up -d --wait --scale backend=1 backend
```

3. Set `JPA_DDL_AUTO=validate` and start the full deployment:

```sh
docker compose -p siga-production -f compose.production.yaml up -d --wait
docker compose -p siga-production -f compose.production.yaml exec nginx nginx -t
docker compose -p siga-production -f compose.production.yaml ps
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
or update any old frontend/base URL pointing at localhost. Changing `.env`
alone does not rewrite an existing client's redirect configuration. Never recreate
users to work around a migration: application profiles depend on their subjects.

With an existing compatible schema, keep `JPA_DDL_AUTO=validate`:

```sh
docker compose -p siga-production -f compose.production.yaml pull
docker compose -p siga-production -f compose.production.yaml up -d --wait
```

Schema changes need a deliberate single-backend maintenance/update step and a
backup, as with initial setup; validation does not migrate a schema.

## Certificate renewal

Certbot remains on the host; no extra container or renewal service is added.
After a successful renewal, run from the repository root:

```sh
docker compose -p siga-production -f compose.production.yaml exec -T nginx nginx -t
docker compose -p siga-production -f compose.production.yaml exec -T nginx nginx -s reload
```

Reload only if the configuration/certificate check succeeds. Mounting the whole
directory lets Nginx see Certbot's new symlink targets; reloading makes its workers
use the renewed certificate without restarting the other application services.
If Certbot uses standalone HTTP validation, it cannot bind port 80 while Nginx is
using it. Keep the validation method used when issuing the certificate; this
configuration does not add a webroot challenge route or change Certbot settings.

## Check from a browser

- `https://sigarojas.duckdns.org/`: application and Keycloak login/logout return correctly.
- `https://sigarojas.duckdns.org/config.json`: API URL is `PUBLIC_URL/api`, Keycloak URL is
  `PUBLIC_URL/auth`; no localhost addresses or secrets.
- `https://sigarojas.duckdns.org/auth/realms/siga/.well-known/openid-configuration`: issuer is
  `PUBLIC_URL/auth/realms/siga`.
- An anonymous `/api/students` request returns 401, proving the API route reaches
  the protected backend rather than the frontend's HTML fallback.

For load-balancing verification, make repeated API requests and inspect
`docker compose -p siga-production -f compose.production.yaml logs nginx`.
The `upstream=` field records the backend IP handling each request; compare the
three IPs with the backend containers on the Compose network. No debug endpoints
or public backend ports are added.

Swagger configuration, cloud resource creation, data migration and certificate
issuance remain separate tasks.
