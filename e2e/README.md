# E2E harness — IRN route authorization

One compose stack runs BOTH apps (this repo's Management API and the Studio API) against two
dedicated Postgres instances and two WireMock mocks (OIDC issuer + IRN backoffice).
Design under test: `docs/SPEC_ROUTE_AUTHORIZATION.md` in each repo.

## Prerequisites

- Docker Desktop
- Jars built on the HOST (the `cv.igrp.framework:*:0.1.0-beta.24.3` artifacts only exist in `~/.m2`,
  so never `mvn` inside Docker):

```sh
(cd .. && mvn -o -DskipTests package)   # if repackage fails offline, drop -o (only boot plugin deps are fetched)
(cd ../../igrp-process-studio-backend-api && mvn -o -DskipTests package)
```

- Keys in `e2e/keys/` (gitignored — regenerate if missing): `./gen-keys.sh` creates the issuer
  RSA pair, the PKCS#1 IRN client key, and rebuilds the JWKS the mock issuer serves.

## Run

```sh
docker compose -p irn-e2e -f docker-compose.e2e.yml up -d
# wait for health:
curl -fsS http://localhost:18080/actuator/health   # mgmt
curl -fsS http://localhost:18082/actuator/health   # studio
```

Teardown: `docker compose -p irn-e2e -f docker-compose.e2e.yml down -v`

## Ports

| Service | Host port |
|---|---|
| Management API | 18080 |
| Studio API | 18082 |
| WireMock IRN (`/api/v1/Auth/me`) | 18089 |
| WireMock OIDC (issuer) | 18090 |
| Postgres mgmt (`igrp_e2e`/`igrp_e2e`/`igrp_e2e`) | 15432 |
| Postgres studio (`studio_e2e`/`studio_e2e`/`studio_e2e`) | 15433 |

## Auth model

A request needs BOTH:

1. `Authorization: Bearer $(./mint-token.sh <sub> [email])` — RS256 JWT, kid `e2e-key`, issuer
   `http://mock-oidc:8080/realms/e2e`. Both apps fetch the JWKS from the mock at startup.
2. `Cookie: session_id=<session>` — the IRN mock maps the cookie to a `/Auth/me` response
   (permissions + email). Unknown/missing cookie gets a 401 body from the mock, which the
   adapter treats as no authorities.

Super admin is decided by `/me` email == `IRN_API_SUPER_ADMIN_EMAIL` (= `superadmin@test.local`,
session `sess-admin`).

**M2M path (24.6):** machine callers skip both credentials above — one header only,
`Authorization: Bearer igrpm2m_<key>`. Mint a key with the super admin
(`POST /m2m-keys` with Bearer `sess-admin` token + its cookie), then call business routes with just
the key: in-permission → 200, out-of-permission → 403, `/m2m-keys` with a key → 403 (structurally
barred), revoked/fake key → 401. Keys live in `t_m2m_api_key` (Flyway V7); the pepper defaults to
empty in e2e.

### Stubbed sessions (wiremock/irn/mappings/)

| session_id | permissions | email |
|---|---|---|
| sess-none | (none) | none@test.local |
| sess-mgmt-viewer | AREAS/PROCESS_DEFINITIONS/PROCESS_INSTANCES/ACTIVITIES/TASK_INSTANCES `:visualizar` | mgmt-viewer@test.local |
| sess-mgmt-creator | TASK_INSTANCES:criar, PROCESS_INSTANCES:criar, AREAS:criar | mgmt-creator@test.local |
| sess-mgmt-publisher | PROCESS_DEFINITIONS:publicar | mgmt-publisher@test.local |
| sess-studio-viewer | STUDIO_PROJECTS:visualizar | studio-viewer@test.local |
| sess-studio-pd | STUDIO_PROCESS_DEFINITIONS:visualizar, :criar | studio-pd@test.local |
| sess-studio-publisher | STUDIO_PROCESS_DEFINITIONS:publicar | studio-publisher@test.local |
| sess-cache-probe-a | AREAS:visualizar — RESERVED for cache tests | cache-probe-a@test.local |
| sess-cache-probe-b | STUDIO_PROJECTS:visualizar — RESERVED for cache tests | cache-probe-b@test.local |
| sess-admin | (none — super admin by email) | superadmin@test.local |

Count `/Auth/me` calls via WireMock admin: `POST http://localhost:18089/__admin/requests/count`
with body `{"method":"GET","urlPath":"/api/v1/Auth/me"}` (or GET `/__admin/requests`).

### Example

```sh
TOKEN=$(./mint-token.sh viewer)
curl -i http://localhost:18080/areas \
  -H "Authorization: Bearer $TOKEN" -H "Cookie: session_id=sess-mgmt-viewer"
# expect non-401/403; sess-none on the same route expects 403
```

## Notes / choices

- Both apps run `SPRING_ACTIVE_PROFILE=development`: in that profile
  `spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_JWT_ISSUER}` is set (both repos), so
  Boot auto-config builds the JwtDecoder from the mock's discovery document. The explicit
  `@Profile("!development & !staging")` JwtDecoder bean in the studio repo never activates.
- The apps are stock `eclipse-temurin` JRE images with the host-built jar volume-mounted; there is
  no image build step, so `up -d` is enough after `mvn package`.
- `depends_on: mock-oidc: service_healthy` matters: the JwtDecoder is built at startup from the
  issuer discovery URL and the app fails (or fails open-to-broken) if the issuer is unreachable.
- Mail/Kafka env vars on mgmt exist only to satisfy property placeholders with no default; the
  broker provider is `none` and nothing sends mail.
