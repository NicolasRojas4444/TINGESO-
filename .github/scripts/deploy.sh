#!/usr/bin/env bash
set -euo pipefail
umask 077

# Runs on the Ubuntu server through SSH. Secrets remain in its .env.
deployment_path=${1:?Deployment directory required}
revision=${2:?Commit SHA required}
export BACKEND_IMAGE=${3:?Backend image required}
export FRONTEND_IMAGE=${4:?Frontend image required}
export APP_PULL_POLICY=always
export COMPOSE_FILE=compose.production.yaml
export COMPOSE_PROJECT_NAME=siga-production
[[ "$deployment_path" =~ ^/[a-zA-Z0-9_/-]+$ && "$deployment_path" != / ]]
[[ "$revision" =~ ^[a-f0-9]{40}$ ]]
[[ "$BACKEND_IMAGE" =~ ^docker[.]io/[a-z0-9_-]+/siga-backend:sha-$revision$ ]]
[[ "$FRONTEND_IMAGE" =~ ^docker[.]io/[a-z0-9_-]+/siga-frontend:sha-$revision$ ]]

cd "$deployment_path"
test -f .env || { echo 'Prepare .env on the server first.'; exit 1; }
archive="/tmp/siga-$revision.tar.gz"
temporary_env=''
cleanup() {
    rm -f "$archive"
    if [[ -n "$temporary_env" ]]; then rm -f "$temporary_env"; fi
}
trap cleanup EXIT

# --overwrite keeps mounted configuration file inodes intact for Nginx reload.
# The archive contains only the five versioned deployment configuration files.
# Public configuration must be readable by the non-root container users.
# Keep the restrictive process umask for secrets and temporary files only.
(umask 022; tar --overwrite -xzf "$archive")
chmod 755 docker docker/nginx docker/postgres docker/keycloak
chmod 644 compose.production.yaml docker/nginx/default.conf \
    docker/postgres/init-keycloak.sh docker/keycloak/siga-realm.json \
    docker/keycloak/setup-registration.sh
chmod 600 .env

compose() {
    docker compose --env-file .env "$@"
}

compose config --quiet
compose pull
compose run --rm --no-deps nginx nginx -t
compose up -d --wait --wait-timeout 360
compose exec -T nginx nginx -t
compose exec -T nginx nginx -s reload

# Persist the successful release so a manual `up` uses the same image versions.
temporary_env=$(mktemp "$deployment_path/.env.XXXXXX")
awk -v backend="$BACKEND_IMAGE" -v frontend="$FRONTEND_IMAGE" '
    /^[[:space:]]*(export[[:space:]]+)?BACKEND_IMAGE[[:space:]]*=/ { next }
    /^[[:space:]]*(export[[:space:]]+)?FRONTEND_IMAGE[[:space:]]*=/ { next }
    /^[[:space:]]*(export[[:space:]]+)?COMPOSE_FILE[[:space:]]*=/ { next }
    /^[[:space:]]*(export[[:space:]]+)?COMPOSE_PROJECT_NAME[[:space:]]*=/ { next }
    { print }
    END {
        print "BACKEND_IMAGE=" backend; print "FRONTEND_IMAGE=" frontend
        print "COMPOSE_FILE=compose.production.yaml"
        print "COMPOSE_PROJECT_NAME=siga-production"
    }
' .env > "$temporary_env"
cat "$temporary_env" > .env
compose ps
echo "Deployed revision $revision."
