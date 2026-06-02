# Security Recommendations

Reviewed on 2026-05-23 for the iGRP Platform Process Management API.

This is a static project review. Validate every recommendation against the target production environment, identity provider, deployment platform, and data-classification rules.

## Priority Summary

| Priority | Area | Recommendation |
| --- | --- | --- |
| P0 | CORS | Restrict allowed origins and avoid credentials with wildcard origins. |
| P0 | Message consumers | Do not process unauthenticated broker messages as admin/system by default. |
| P0 | Task/process access | Enforce authorization on task search, claim, assign, complete, import, deploy, and admin-style operations. |
| P0 | Webhooks | Add SSRF protections, host allowlists, HTTPS enforcement, timeouts, and response-size limits. |
| P1 | Secrets | Remove default secrets and use Kubernetes/Docker secrets or a vault. |
| P1 | Logging/errors | Stop logging sensitive payloads and sanitize ProblemDetail responses. |
| P1 | Dependencies/images | Align security dependency versions with the Spring Boot BOM and scan dependencies/images. |
| P1 | Transport security | Require TLS for ingress, Kafka, webhooks, IAM calls, and mail where applicable. |
| P2 | Swagger/actuator | Keep docs and operational endpoints disabled or protected outside development. |
| P2 | Input limits | Add strict validation for variable filters, JSON payload size, dates, enum values, and webhook headers. |

## Authentication And Authorization

### Restrict CORS in production

`SecurityConfig` currently allows all origins with credentials enabled:

- `configuration.addAllowedOriginPattern(CorsConfiguration.ALL)`
- `configuration.setAllowCredentials(true)`

That combination is dangerous for browser clients because any origin pattern may be accepted while credentialed requests are allowed.

Recommended actions:

- Configure allowed origins per environment, for example `https://app.example.cv`.
- Use `allowedOrigins` for exact trusted origins where possible.
- Keep `allowCredentials=false` unless browser credentials are truly required.
- Keep allowed methods and headers minimal.
- Add integration tests that verify disallowed origins are rejected.

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

### Fail closed when authorization enrichment fails

`SecurityConfig#jwtAuthenticationConverter` catches authorization-service errors and still grants `ROLE_ACTIVITI_USER`.

Recommended actions:

- Fail closed for endpoints that need permissions.
- If availability requires degraded access, grant only a minimal role with no write privileges.
- Track authorization enrichment failures as security alerts.
- Cache authorization lookups carefully with short TTL and user/token scoping.

### Review broker message authentication

`AbstractStartProcessConsumer` and `AbstractProcessEventConsumer` use a system authentication with `ROLE_ACTIVITI_USER` and `ROLE_ACTIVITI_ADMIN` when no `Authorization` header exists. Their fallback `extractAuthorities` also grants admin/user roles when roles are missing.

Risk:

- Any producer that can write to the topic/queue may start or signal processes with admin-like authority.

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

Recommended actions:

- Remove the default token value.
- Fail startup in staging/production when a required secret is missing.
- Rotate any token that may have used the default.
- Keep tokens out of logs, process variables, and Postman exports.

### Use secrets for Kubernetes credentials

`k8s/deployment.yaml` contains placeholder database credentials directly in env values.

Recommended actions:

- Use Kubernetes `Secret` for database passwords, mail credentials, Kafka credentials, and webhook tokens.
- Use `ConfigMap` only for non-secret values.
- Add `envFrom` or `valueFrom.secretKeyRef`.
- Prevent real secret values from entering Git.

### Keep `.env` local only

`.env` is ignored by Git, which is good. `.env.example` contains sample credentials and debug defaults.

Recommended actions:

- Keep `.env.example` clearly fake and non-reusable.
- Avoid realistic passwords like `softwaredeveloper` or `password`.
- Set `LOGGING_LEVEL_APP` and `LOGGING_LEVEL_SPRING_WEB` examples to `INFO`.
- Include required secret names without values where possible.

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

- Full broker messages in consumers.
- Full webhook payloads at debug level.
- Webhook response bodies in info/warn logs.
- Full DTO content after process start.
- Full authorities at debug level.

Recommended actions:

- Mask tokens, passwords, authorization headers, emails where required, business keys if sensitive, variables/forms, and webhook payloads.
- Log IDs and correlation IDs instead of full payloads.
- Add structured logging with a redaction policy.
- Keep debug logging disabled in shared environments.

### Sanitize API error responses

`GlobalExceptionHandler` sometimes returns exception messages or database-specific details to clients.

Recommended actions:

- Return stable public error codes/messages to clients.
- Log detailed internal errors server-side only.
- Avoid returning stack trace locations, raw SQL errors, or malformed JSON parser internals.
- Keep validation errors user-friendly, but do not echo sensitive submitted values.

## Deployment Security

### Pin and scan container images

The Dockerfile uses `cgr.dev/chainguard/maven:latest-dev` and `cgr.dev/chainguard/jre:latest`.

Recommended actions:

- Pin exact tags or digests.
- Scan application images with Trivy, Grype, or the organization-approved scanner.
- Generate an SBOM during CI.
- Verify the runtime image runs as non-root.
- Use read-only root filesystem where possible.

### Harden Kubernetes manifests

Recommended additions:

- TLS-enabled ingress and HSTS.
- Readiness and liveness probes.
- `securityContext` with `runAsNonRoot`, `allowPrivilegeEscalation: false`, dropped capabilities, and seccomp profile.
- NetworkPolicies that limit DB, broker, IAM, and webhook egress.
- Resource requests/limits matched to load tests.
- Separate service accounts with least privilege.

### Protect actuator and Swagger

Production disables Swagger in `application-production.properties`, which is good. Staging enables Swagger by default unless `ENABLE_SWAGGER` is false.

Recommended actions:

- Keep Swagger disabled or authenticated outside development.
- Keep `/actuator/health` public only if it does not expose details.
- Protect `/actuator/prometheus` by auth, network policy, or internal-only ingress.
- Disable `show-details` for unauthenticated health responses.

## Dependency And Build Security

### Align dependency versions

The project uses Spring Boot's dependency management but also pins `spring-security-oauth2-client` to `6.3.7`.

Recommended actions:

- Avoid overriding Spring Security versions unless there is a documented compatibility reason.
- Let the Spring Boot BOM manage Spring Security where possible.
- If overriding, document why and check for CVEs.

### Tighten vulnerability gates

OWASP Dependency Check is configured with `failBuildOnCVSS=10`.

Recommended actions:

- Lower the threshold for release branches, for example fail on `CVSS >= 7` or organization policy.
- Add dependency updates through Renovate, Dependabot, or equivalent.
- Scan Docker images and OS packages, not only Maven dependencies.
- Keep private repository dependencies monitored for advisories.

## Transport And Data Protection

### Require TLS for all external connections

Recommended actions:

- Use HTTPS for IAM, access-management APIs, webhooks, and OTLP where supported.
- Use `SASL_SSL` or `SSL` for Kafka in shared/prod environments.
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

1. Restrict CORS origins and add tests.
2. Remove admin/system fallback for unauthenticated broker messages.
3. Enforce task/process visibility for non-admin users.
4. Remove default webhook token and move deployment secrets to `Secret` references.
5. Add SSRF protections and timeouts to webhook delegates.
6. Sanitize logs and public error responses.
7. Pin container images and tighten dependency/image scanning gates.
