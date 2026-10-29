#!/bin/bash
set -euo pipefail

# Configure the technical account on existing volumes as well as fresh imports.
# This creates no academic/demo users and does not replace the realm.
cli() {
  /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/siga-registration-kcadm.config
}

cli config credentials \
  --server "$KEYCLOAK_SERVER_URL" \
  --realm master \
  --user "$KEYCLOAK_ADMIN" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"

client_id=$(cli get clients -r "$KEYCLOAK_REALM" \
  -q clientId=siga-backend-registration --fields id --format csv --noquotes)
client_id=${client_id//$'\r'/}

if [[ -z "$client_id" ]]; then
  client_id=$(cli create clients -r "$KEYCLOAK_REALM" \
    -s clientId=siga-backend-registration \
    -s publicClient=false \
    -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s 'defaultClientScopes=["basic","roles"]' \
    -s "secret=$KEYCLOAK_REGISTRATION_CLIENT_SECRET" -i)
else
  cli update "clients/$client_id" -r "$KEYCLOAK_REALM" \
    -s publicClient=false \
    -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s "secret=$KEYCLOAK_REGISTRATION_CLIENT_SECRET"
fi

cli add-roles -r "$KEYCLOAK_REALM" \
  --uusername service-account-siga-backend-registration \
  --cclientid realm-management \
  --rolename manage-users \
  --rolename view-realm

echo 'Keycloak registration service account configured.'
