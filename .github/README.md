# Automated tests, publication and deployment

Pull requests to `main` run `test.yml`. A push to `main` (or a manual run of
`deploy.yml` on `main`) runs the same tests, publishes both Docker Hub images only
after all test jobs pass, then deploys that exact commit through verified SSH.
Both images are published for Linux amd64 and arm64 under the same commit tag;
Docker selects the correct variant for the server (including ARM64 Ubuntu).
Build stages run natively and produce portable Java bytecode/static frontend
assets; QEMU supports target-architecture runtime build steps. Nothing uses
npm/PM2 on the server.

## GitHub configuration

Under **Settings > Secrets and variables > Actions**, configure:

Repository variables:

| Name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub account owning `siga-backend` and `siga-frontend` |
| `DEPLOY_PATH` | Existing absolute server directory, e.g. `/home/ubuntu/siga` (no spaces) |

Repository secrets:

| Name | Value |
|---|---|
| `DOCKERHUB_TOKEN` | Docker Hub access token with permission to publish both repositories |
| `SSH_HOST` | Server IP or DNS hostname, without protocol or port |
| `SSH_USER` | Server deployment user |
| `SSH_PORT` | Optional SSH port; default 22 |
| `SSH_PRIVATE_KEY` | Complete deployment private key, without a passphrase |
| `SSH_KNOWN_HOSTS` | Verified OpenSSH known_hosts entry for this server |

`DOCKERHUB_USERNAME` may alternatively be a repository Secret with the same name.
If both exist, the Variable takes precedence. For this account its value should
be `donyetta`. `DOCKERHUB_TOKEN` must be a Docker Hub access token with write
permission for both image repositories. A login performed on your PC/server does
not authenticate the GitHub runner. An empty `.secrets` file is not the cause by
itself: GitHub does not load that file at all.

If publication still fails after configuring these values, inspect the failing
step: missing-configuration messages identify absent entries; an unauthorized or
denied Docker Hub response usually requires checking the token, account and
repository permissions. Do not post the token in logs or chat.

Verify the SSH host key fingerprint through a trusted channel (server console)
before saving its known_hosts entry. For a nonstandard port the entry must use
`[hostname]:port`. The workflow never disables host-key checking. Restrict the
deployment key to the intended server/account. No keys belong in `.secrets`;
that local file is ignored by Git and is not read by Actions.

## One-time server preparation

1. Install Docker Engine/Compose as described in
   [the deployment guide](../docker/nginx/README.md). The SSH user must be able to
   run Docker without an interactive sudo prompt and write to `DEPLOY_PATH`.
   Docker group access is effectively root access; use a trusted deployment user.
2. Authorize the public SSH key for that user, and permit SSH from the runner.
   The private key stays in GitHub Secrets.
3. Prepare the single `DEPLOY_PATH/.env`, including the existing domain,
   database/Keycloak secrets and Certbot directory. Never commit it. If images are
   private, perform `docker login` once on the server as the deployment user using
   a read-only Docker Hub token; the workflow does not send its publishing token.
4. No manual database/schema bootstrap is needed: Compose setup dependencies
   prepare the Keycloak database and run one schema-update job before the three
   normal backend replicas. Existing passwords must still match `.env`. Data
   restoration and incompatible/destructive schema migrations remain deliberate
   administrative operations; take backups before updates.

Each automatic deployment transfers only Compose, Nginx, the PostgreSQL init
script, and the two Keycloak configuration files. It never transfers `.env`,
certificates, the local database or test accounts, and never runs `down -v`.
Nginx configuration/certificates are checked before updating services. Successful
image tags are saved in the server `.env` for subsequent manual use.
Extracted public configuration is set to mode 644 and its directories to 755 so
container users can read bind mounts; `.env` remains private (600). Before
starting Keycloak, Compose runs the idempotent database setup service to recover
an interrupted first setup. Existing database roles and passwords are not replaced.
Actions uses the same startup dependency graph as manual execution, with
`APP_PULL_POLICY=always` to select published images rather than local source builds.
It also saves `COMPOSE_FILE` and `COMPOSE_PROJECT_NAME` in `.env` after success.

Image tags are `docker.io/<username>/siga-backend:sha-<full-commit-sha>` and
`docker.io/<username>/siga-frontend:sha-<full-commit-sha>`. There is no ambiguous
`latest` tag. Failed tests prevent publication; failed publication prevents SSH
deployment. Failed deployments fail the workflow; there is no automatic rollback.
Read the logs and verify container health before retrying.

## Manual deployment remains available

On the server, update the full source checkout and configure `.env` as in the root
README (`COMPOSE_FILE=compose.production.yaml`, `COMPOSE_PROJECT_NAME=siga-production`
and `APP_PULL_POLICY=build`). Then:

```sh
docker compose up --build -d
```

Actions transfers deployment configuration, not the Java/React source tree.
Update that full checkout before manually building from source. To deploy only
published images manually instead, set `APP_PULL_POLICY=always` with the desired
matching real image tags and use `docker compose up -d --pull always`.
Reload Nginx after changing its config. Keep the project name unchanged to preserve
the correct database volume.

The assignment requires CD ending at Docker Hub and manual deployment. SSH
deployment is the additional automation explicitly requested for this repository;
the manual procedure remains supported.
