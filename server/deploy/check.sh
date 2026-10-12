#!/usr/bin/env bash
# Run after public HTTPS deployment; must not send secrets into logs.
set -euo pipefail
if [ "$#" -ne 1 ]; then
  printf 'Usage: ./check.sh https://your-trial-domain.example\n' >&2
  exit 2
fi
url="${1%/}"
case "$url" in
  https://*) ;;
  *) printf 'HTTPS URL required\n' >&2; exit 2;;
esac
echo "Checking $url/health"
curl --fail --show-error --silent --max-time 15 --proto '=https' "$url/health"
echo
echo "Checking nonce endpoint (do not log nonce values)"
curl --fail --show-error --silent --max-time 15 --proto '=https' \
  -o /dev/null -w 'Nonce HTTP status: %{http_code}\n' \
  "$url/v1/auth/nonce"
echo "Checking unauthenticated access is denied"
status="$(curl --silent --max-time 15 --proto '=https' -o /dev/null \
  -w '%{http_code}' "$url/v1/entitlements")"
if [ "$status" != "401" ]; then
  echo "FAIL: entitlement endpoint returned $status, expected 401" >&2
  exit 1
fi
echo "PASS: only authenticated sessions may access trial entitlements"
