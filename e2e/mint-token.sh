#!/usr/bin/env bash
# mint-token.sh <sub> [email] — prints an RS256 JWT signed with e2e/keys/issuer-key.pem
# Header: {"alg":"RS256","typ":"JWT","kid":"e2e-key"}
# Claims: iss=http://mock-oidc:8080/realms/e2e, sub, email, preferred_username, exp=now+3600
set -euo pipefail
SUB="${1:?usage: mint-token.sh <sub> [email]}"
EMAIL="${2:-${SUB}@test.local}"
DIR="$(cd "$(dirname "$0")" && pwd)"
KEY="$DIR/keys/issuer-key.pem"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

NOW=$(date +%s)
EXP=$((NOW + 3600))
HEADER=$(printf '{"alg":"RS256","typ":"JWT","kid":"e2e-key"}' | b64url)
PAYLOAD=$(printf '{"iss":"http://mock-oidc:8080/realms/e2e","sub":"%s","email":"%s","preferred_username":"%s","iat":%d,"exp":%d}' \
  "$SUB" "$EMAIL" "$SUB" "$NOW" "$EXP" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" | openssl dgst -sha256 -sign "$KEY" -binary | b64url)
printf '%s.%s.%s\n' "$HEADER" "$PAYLOAD" "$SIG"
