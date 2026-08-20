#!/usr/bin/env bash
# Regenerates e2e/keys/ (gitignored) and the JWKS served by the mock OIDC issuer.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$DIR/keys"
cd "$DIR/keys"
openssl genrsa -out issuer-key.pem 2048                    # OIDC issuer signing key
openssl genrsa -traditional -out irn-client-key.pem 2048   # PKCS#1 key for the signed IRN RestClient
python3 - <<'EOF'
import base64, json, subprocess
mod_hex = subprocess.run(['openssl','rsa','-in','issuer-key.pem','-noout','-modulus'],
                         capture_output=True, text=True, check=True).stdout.strip().split('=')[1]
def b64url(i):
    return base64.urlsafe_b64encode(i.to_bytes((i.bit_length()+7)//8, 'big')).rstrip(b'=').decode()
jwks = {"keys": [{"kty": "RSA", "kid": "e2e-key", "use": "sig", "alg": "RS256",
                  "n": b64url(int(mod_hex, 16)), "e": b64url(65537)}]}
open('jwks.json', 'w').write(json.dumps(jwks))
open('../wiremock/oidc/__files/jwks.json', 'w').write(json.dumps(jwks))
print("wrote keys/jwks.json and wiremock/oidc/__files/jwks.json")
EOF
echo "done — restart mock-oidc if the stack is running"
