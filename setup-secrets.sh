#!/bin/bash

SECRETS_DIR="$(dirname "$0")/secrets"

echo "=== teslaHitch Secret Configuration ==="
echo

read -rp "Tesla Client ID: " client_id
read -rp "Tesla Client Secret: " client_secret
read -rp "OAuth Redirect URI: " redirect_uri

mkdir -p "$SECRETS_DIR"

printf '%s' "$client_id" > "$SECRETS_DIR/tesla.oauth.clientId"
printf '%s' "$client_secret" > "$SECRETS_DIR/tesla.oauth.clientSecret"
printf '%s' "$redirect_uri" > "$SECRETS_DIR/tesla.oauth.redirectUri"

echo
echo "Secrets written to $SECRETS_DIR/"
