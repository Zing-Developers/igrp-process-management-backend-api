# Security Recommendations

Reviewed on 2026-06-02 for the iGRP Platform Process Management API.
**Status updated 2026-08-24** after the security-hardening branch (route authorization, log hygiene,
CVE remediation — see docs/CHANGELOG.md). DONE items reference the closing work.

This is a static project review. Validate every recommendation against the target production environment, identity provider, deployment platform, and data-classification rules.

## Priority Summary

| Priority | Area | Recommendation | Estado 2026-08 |
| --- | --- | --- | --- |
| P0 | JWT decoder | Fail startup when the OIDC provider is unreachable instead of silently disabling JWT validation. | **FEITO** — try/catch removido; arranque falha se o OIDC estiver inacessível |
| P0 | CORS | Restrict allowed origins and avoid credentials with wildcard origins. | **FEITO** — origins por `IGRP_CORS_ALLOWED_ORIGINS` (vazio = sem cross-origin); wildcard eliminado nas duas apps |
| P0 | Message consumers | Do not process unauthenticated broker messages as admin/system by default. | **ABERTO** — system-bot ainda ganha ROLE_ACTIVITI_ADMIN |
| P0 | Task/process access | Enforce authorization on task search, claim, assign, complete, import, deploy, and admin-style operations. | **FEITO** — autorização por permissões IRN + guard de visibilidade (SPEC_ROUTE_AUTHORIZATION.md) |
| P0 | Webhooks | Add SSRF protections, host allowlists, HTTPS enforcement, timeouts, and response-size limits. | **PARCIAL** — timeouts feitos (5s/10s); allowlist/CIDR/HTTPS/limites em falta |
| P1 | Secrets | Remove default secrets and use Kubernetes/Docker secrets or a vault. | **FEITO (código)** — default do token removido; placeholder forte no .env.example; vault/Secrets é prática de deployment |
| P1 | Logging/errors | Stop logging sensitive payloads and sanitize ProblemDetail responses. | **FEITO** — logs PII-estritos + ProblemDetail saneado |
| P1 | Error responses | Do not return internal exception messages, PSQL details, or parser internals to API clients. | **FEITO** — NPE/ISE/PSQL/parser genéricos (log servidor); IAE e exceções de engine mantêm mensagens de negócio deliberadamente |
| P1 | Dependencies/images | Align security dependency versions with the Spring Boot BOM and scan dependencies/images. | **FEITO** — pin 6.3.7 removido, 51 CVEs Accenture, gate OWASP CVSS ≥ 7 |
| P1 | Transport security | Require TLS for ingress, Kafka, webhooks, IAM calls, and mail where applicable. | **ABERTO** |
| P1 | Kafka transport | Default Kafka security protocol to SASL_SSL instead of PLAINTEXT. | **ABERTO** — default continua PLAINTEXT |
| P2 | Profile defaults | Default active profile to production instead of development to prevent accidental ddl-auto=update and show-sql in production. | **FEITO** — default production nas duas apps |
| P2 | Swagger/actuator | Keep docs and operational endpoints disabled or protected outside development. | **FEITO** — staging default false; /actuator/prometheus coberto pelo denyAll do adapter IRN |
| P2 | Input limits | Add strict validation for variable filters, JSON payload size, dates, enum values, and webhook headers. | **ABERTO** |
| P2 | Dockerfile | Pin container image tags, review keystore password usage, and run as non-root. | **FEITO** — tags pinadas + USER 1001 non-root nas duas |

## Authentication And Authorization

### JWT decoder must fail closed on startup

`JwtDecoderConfiguration` catches all exceptions during `NimbusJwtDecoder` construction and returns a lambda that throws `JwtException` for every token. If the OIDC provider (Keycloak) is unreachable at startup, the application will permanently reject all JWTs until restarted, with no health indicator or metric reporting this state.

**File:** `src/main/java/cv/igrp/platform/process/management/shared/security/util/JwtDecoderConfiguration.java:22-30`

```java
} catch (Exception e) {
  return token -> {
    throw new JwtException("JWT validation temporarily disabled (Keycloak unavailable)");
  };
}
```

Risk:

- Silent authentication failure with no alerting; operational pressure to bypass auth checks.
- No metric or health indicator signals this degraded state.

Recommended actions:

- Remove the try/catch. Let the startup fail fast when the auth server is unreachable.
- Use Spring Boot readiness probes (`/actuator/health/readiness`) to signal not-ready.
- If lazy initialization is needed, perform OIDC discovery inside the `decode()` call with retry logic, not a catch-all at bean creation.

### Restrict CORS in production

`SecurityConfig` currently allows all origins with credentials enabled:

```java
configuration.addAllowedOriginPattern(CorsConfiguration.ALL);   // "*"
configuration.addAllowedHeader(CorsConfiguration.ALL);
configuration.setAllowCredentials(true);
```

**File:** `src/main/java/cv/igrp/platform/process/management/shared/security/SecurityConfig.java:58-71`

That combination is dangerous for browser clients because any origin pattern may be accepted while credentialed requests are allowed. An attacker-controlled site can make authenticated cross-origin requests using the victim's auth context.

Recommended actions:

- Configure allowed origins per environment, for example `https://app.example.cv`.
- Use `allowedOrigins` for exact trusted origins where possible.
- Keep `allowCredentials=false` unless browser credentials are truly required.
- Keep allowed methods and headers minimal.
- Add integration tests that verify disallowed origins are rejected.

### Fail closed when authorization enrichment fails

`SecurityConfig#jwtAuthenticationConverter` catches authorization-service errors and still grants `ROLE_ACTIVITI_USER`:

```java
} catch (Exception e) {
    LOGGER.error("Failed to enrich authorities for [email={}, sub={}]: {}", email, sub, e.getMessage(), e);
    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_USER));
}
```

**File:** `src/main/java/cv/igrp/platform/process/management/shared/security/SecurityConfig.java:150-154`

Recommended actions:

- Fail closed for endpoints that need permissions.
- If availability requires degraded access, grant only a minimal role with no write privileges.
- Track authorization enrichment failures as security alerts.
- Cache authorization lookups carefully with short TTL and user/token scoping.

### Add method-level authorization

The global rule is authenticated by default, but no active `@PreAuthorize` usage was found. Imports exist in generated controllers, but endpoint-specific authorization appears absent.

High-risk endpoints include:

- Task search, details, variables, claim, unclaim, assign, save, and complete.
- Process start, cancel, archive, event trigger, and statistics.
- Process definition import, deploy, assign, archive, and artifact operations.
- Area/process association management.

Recommended actions:

- Enable method security if not already enabled by framework code.
- Add permission checks using authorities populated by `IAuthorizationServiceAdapter`.
- Enforce ownership/group visibility inside services, not only in controllers.
- Add tests for normal user, candidate-group user, process owner, super admin, and unauthorized user.

### Lock down task search visibility

`TaskInstancesController#listTaskInstances` accepts query params such as `user`, `candidateGroups`, and `filterByCurrentUser`. `TaskInstanceService#getAllTaskInstances` only binds current-user visibility when `filterByCurrentUser` is true.

Risk:

- A regular authenticated user may be able to search broader task sets unless another authorization layer blocks it.

Recommended actions:

- Default task search to current-user visibility for non-admin users.
- Ignore client-supplied `user` and `candidateGroups` unless the user has an admin/search-all permission.
- Add a service-level guard that forces visibility rules even if a controller or command forgets them.
- Add tests proving users cannot list or inspect tasks outside their assignments/groups.

### Review broker message authentication

`AbstractStartProcessConsumer` and `AbstractProcessEventConsumer` use a system authentication with `ROLE_ACTIVITI_USER` and `ROLE_ACTIVITI_ADMIN` when no `Authorization` header exists. Their fallback `extractAuthorities` also grants admin/user roles when roles are missing.

**Files:**
- `src/main/java/cv/igrp/platform/process/management/shared/delegates/message/consumer/AbstractStartProcessConsumer.java:153-158`
- `src/main/java/cv/igrp/platform/process/management/shared/delegates/message/consumer/AbstractProcessEventConsumer.java:143-148`

```java
protected Authentication systemAuthentication() {
    return new UsernamePasswordAuthenticationToken(
        "system-bot", null,
        List.of(new SimpleGrantedAuthority("ROLE_ACTIVITI_USER"),
                new SimpleGrantedAuthority("ROLE_ACTIVITI_ADMIN"))
    );
}
```

Risk:

- Any producer that can write to the topic/queue may start or signal processes with admin-like authority.
- Kafka `security.protocol` defaults to `PLAINTEXT`, so there is no broker-level authentication.

Recommended actions:

- Require signed JWT or mTLS-authenticated broker identity for process-start and event topics.
- Remove admin role from fallback authentication.
- Reject messages without trusted authentication unless the topic is private and ACL-restricted.
- Use Kafka/RabbitMQ ACLs so only approved producers can publish.
- Add event signatures or a shared message authentication mechanism if cross-service JWT is not available.
- Preserve the authenticated subject when calling process services instead of always using a system user.

## Secrets And Configuration

### Remove default secrets

`application.properties` has a default webhook token value:

```properties
igrp.delegate.webhook.auth-token=${IGRP_DELEGATE_WEBHOOK_AUTH_TOKEN:delegate-secret-token}
```

**File:** `src/main/resources/application.properties:62`

Any deployment that does not set `IGRP_DELEGATE_WEBHOOK_AUTH_TOKEN` will authenticate to external webhook endpoints with this publicly visible credential.

Recommended actions:

- Remove the default token value so startup fails when the secret is missing.
- Rotate any token that may have used the default.
- Keep tokens out of logs, process variables, and Postman exports.

### Keep `.env` local only

`.env` is ignored by Git, which is good. `.env.example` contains sample credentials and debug defaults.

Recommended actions:

- Keep `.env.example` clearly fake and non-reusable.
- Replace realistic passwords like `softwaredeveloper` with obviously fake placeholders like `CHANGE_ME_STRONG_PASSWORD_HERE`.
- Set `LOGGING_LEVEL_APP` and `LOGGING_LEVEL_SPRING_WEB` examples to `INFO`.
- Include required secret names without values where possible.

### Default active profile should be production-safe

`application.properties` defaults to the development profile:

```properties
spring.profiles.active=${SPRING_ACTIVE_PROFILE:development}
```

**File:** `src/main/resources/application.properties:10`

The development profile enables `spring.jpa.hibernate.ddl-auto=update` and `spring.jpa.show-sql=true`. Any deployment that fails to set `SPRING_ACTIVE_PROFILE` runs with auto-DDL and SQL logging active.

**File:** `src/main/resources/application-development.properties:33-35`

Risk:

- Accidental schema modification in production if the environment variable is missing.
- SQL query logging may expose sensitive parameter values in log aggregators.

Recommended actions:

- Make the production profile the safe default, or fail startup if `SPRING_ACTIVE_PROFILE` is not set.
- Never use `ddl-auto=update` as a fallback for unset profiles.

## Webhook And SSRF Controls

Webhook delegates build outbound URLs from process variables or BPMN expressions:

- `IgrpWebhookDelegate`
- `IgrpProcessWebhookDelegate`

Risk:

- A process definition or runtime variable may call internal services, cloud metadata endpoints, loopback addresses, or unexpected third-party URLs.

Recommended actions:

- Allowlist webhook hostnames per environment.
- Require HTTPS except for explicit local development profiles.
- Block loopback, link-local, private CIDR ranges, and metadata IPs.
- Restrict HTTP methods to the minimum required.
- Limit request body size and response body size.
- Configure connect/read timeouts.
- Prevent redirects to untrusted hosts.
- Sanitize logs so payloads, headers, tokens, and response bodies are not printed.
- Consider per-process webhook permissions.

### Restrict custom webhook headers

`webhookPayloadHeader` can add arbitrary headers. That can be useful, but it can also override authentication headers or inject sensitive values into untrusted destinations.

Recommended actions:

- Allow only a safe set of headers, for example `Content-Type`, correlation IDs, and approved business headers.
- Block `Authorization`, `Cookie`, and proxy headers unless explicitly allowed.
- Store destination-specific credentials in a secret manager, not in process variables.

## Logging And Error Handling

### Reduce sensitive logging

Observed sensitive logging patterns include:

- Full broker messages in consumers at INFO level.
- Full webhook payloads at debug level.
- Webhook response bodies in info/warn logs.
- Full DTO content after process start.
- Full authorities at debug level.
- Super admin grant logged with user email at INFO level.

**Files:**
- `src/main/java/cv/igrp/platform/process/management/shared/delegates/message/consumer/AbstractStartProcessConsumer.java:47`
- `src/main/java/cv/igrp/platform/process/management/shared/delegates/message/consumer/AbstractProcessEventConsumer.java:46`

```java
LOGGER.info("Received message: {}", message);      // full Kafka payload at INFO
LOGGER.info("Received process event: {}", message); // full Kafka payload at INFO
```

Recommended actions:

- Log IDs and correlation IDs instead of full payloads.
- Move payload logging to DEBUG level only.
- Mask tokens, passwords, authorization headers, emails, business keys, variables/forms, and webhook payloads.
- Add structured logging with a redaction policy.
- Keep debug logging disabled in shared environments.

### Sanitize API error responses

`GlobalExceptionHandler` returns internal exception messages or database-specific details to clients in multiple handlers.

**File:** `src/main/java/cv/igrp/platform/process/management/shared/domain/exceptions/GlobalExceptionHandler.java`

Issues found:

1. **NullPointerException** (line 68-77): `problemDetail.setTitle(ex.getMessage())` — NPE messages with helpful NullPointerExceptions (JDK 14+) contain internal field names, method names, and class paths.

2. **IllegalStateException** (line 79-89): `problemDetail.setTitle(ex.getMessage())` — same internal detail leakage.

3. **IllegalArgumentException** (line 36-46): `problemDetail.setTitle(ex.getMessage())` — may contain internal parameter names or values.

4. **DataIntegrityViolationException** (line 148-175): `problem.setDetail(ex.getMostSpecificCause().getMessage())` — for non-FK errors, the raw PSQL error message (including table names, column names, constraint names, and potentially data values) is returned to the client.

5. **HttpMessageNotReadableException** (line 121-146): `problem.setDetail(ex.getMessage())` — returns Jackson parser internals, class paths, and deserialization details.

6. **RuntimeProcessEngineException** (line 198-206) and **ProcessDeploymentException** (line 208-216): Both set `problemDetail.setTitle(ex.getMessage())` — may expose internal engine state.

Recommended actions:

- Return stable public error codes/messages to clients.
- Log detailed internal errors server-side only.
- Avoid returning stack trace locations, raw SQL errors, or malformed JSON parser internals.
- Keep validation errors user-friendly, but do not echo sensitive submitted values.

## Transport And Data Protection

### Require TLS for Kafka connections

Kafka `security.protocol` defaults to `PLAINTEXT` in the base configuration:

```properties
spring.kafka.properties.security.protocol=${SPRING_KAFKA_SECURITY_PROTOCOL:PLAINTEXT}
```

**File:** `src/main/resources/application.properties:44`

All Kafka messages — including process event data and bearer tokens in `Authorization` headers — are transmitted in cleartext. The SASL JAAS config is always loaded even when the protocol is PLAINTEXT.

Recommended actions:

- Change the default to `SASL_SSL` for the base/production configuration.
- Override to `PLAINTEXT` only in the development profile.
- Ensure SASL JAAS config is conditionally loaded only when SASL is active.

### Require TLS for all other external connections

Recommended actions:

- Use HTTPS for IAM, access-management APIs, webhooks, and OTLP where supported.
- Use TLS for PostgreSQL when traffic crosses nodes or networks where encryption is required.
- Keep SMTP STARTTLS enabled and verify certificate behavior.

### Protect process variables and audit history

Process variables, forms, webhook payloads, and Envers/Activiti history may contain personal or sensitive business data.

Recommended actions:

- Classify which variables may contain PII or secrets.
- Do not store secrets in process variables.
- Add retention/deletion rules for completed workflows.
- Restrict DB access to audit/history tables.
- Consider field-level encryption for high-risk values.

## Deployment Security

### Pin and scan container images

The Dockerfile uses `maven:3.9.9-eclipse-temurin-23` and `eclipse-temurin:23-jre`.

**File:** `Dockerfile`

The Dockerfile also imports custom CA certificates into the JVM truststore using the well-known default password `changeit`:

```dockerfile
-storepass changeit -noprompt \
```

**File:** `Dockerfile:21`

Recommended actions:

- Pin exact tags or digests for reproducible builds.
- Scan application images with Trivy, Grype, or the organization-approved scanner.
- Generate an SBOM during CI.
- Verify the runtime image runs as non-root (add `USER` directive).
- Use read-only root filesystem where possible.
- Pass the keystore password as a build argument, or document why `changeit` is acceptable (CA truststore only, no private keys).

### Protect actuator and Swagger

Production disables Swagger in `application-production.properties`, which is good. Staging defaults Swagger to enabled:

```properties
springdoc.swagger-ui.enabled=${ENABLE_SWAGGER:true}
```

**File:** `src/main/resources/application-staging.properties:30`

The Swagger UI endpoints (`/swagger-ui/**`, `/v3/api-docs/**`) are `permitAll()` in SecurityConfig, meaning full API documentation is visible to unauthenticated visitors.

Recommended actions:

- Default Swagger to disabled in staging (set to `false`).
- Keep `/actuator/health` public only if it does not expose details.
- Protect `/actuator/prometheus` by auth, network policy, or internal-only ingress.
- Disable `show-details` for unauthenticated health responses.
- Consider running the management server on a separate port for internal-only access.

## Dependency And Build Security

### Align dependency versions

The project uses Spring Boot's dependency management but also pins `spring-security-oauth2-client` to a specific version.

Recommended actions:

- Avoid overriding Spring Security versions unless there is a documented compatibility reason.
- Let the Spring Boot BOM manage Spring Security where possible.
- If overriding, document why and check for CVEs.

### Tighten vulnerability gates

OWASP Dependency Check is configured with `failBuildOnCVSS=10`, meaning only a perfect CVSS 10.0 score fails the build. Every CRITICAL (9.x), HIGH, and MEDIUM vulnerability passes silently.

**File:** `pom.xml:281`

```xml
<failBuildOnCVSS>10</failBuildOnCVSS>
```

Recommended actions:

- Lower the threshold for release branches, for example fail on `CVSS >= 7`.
- Add dependency updates through Renovate, Dependabot, or equivalent.
- Scan Docker images and OS packages, not only Maven dependencies.
- Keep private repository dependencies monitored for advisories.

## Input Validation

Recommended actions:

- Limit JSON body size at server/proxy level.
- Limit number and depth of task variable filters.
- Validate variable filter names against an allowlist or safe path regex.
- Validate date formats and reject impossible ranges.
- Validate webhook URLs before building requests.
- Add maximum lengths to strings used in `LIKE` filters.
- Add tests for malformed JSON, invalid enum values, huge payloads, and unauthorized filter combinations.

## Suggested Next Steps

1. Remove the try/catch in `JwtDecoderConfiguration` — fail startup when the OIDC provider is unreachable.
2. Restrict CORS origins and add tests.
3. Remove admin/system fallback for unauthenticated broker messages.
4. Enforce task/process visibility for non-admin users.
5. Remove default webhook token and move deployment secrets to `Secret` references.
6. Sanitize all `GlobalExceptionHandler` responses — stop returning `ex.getMessage()` to clients.
7. Add SSRF protections and timeouts to webhook delegates.
8. Default Kafka security protocol to `SASL_SSL` and active profile to production.
9. Pin container images, add non-root `USER` directive, and lower OWASP CVSS threshold to 7.
10. Sanitize logs — move payload logging to DEBUG, redact tokens and PII.
11. Disable Swagger in staging by default.
