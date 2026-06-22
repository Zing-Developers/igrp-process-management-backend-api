# Performance Recommendations

Reviewed on 2026-06-08 for the iGRP Platform Process Management API.

This project is a Spring Boot 3 / Java 25 service with JPA/PostgreSQL, Activiti runtime integration, Kafka/RabbitMQ delegates, Caffeine cache dependencies, Flyway, Envers auditing, OpenTelemetry, Docker, and Kubernetes manifests.

## Priority Summary

| Priority | Area | Recommendation | Status |
| --- | --- | --- | --- |
| P0 | N+1 queries — task list | Batch process-variable and user-profile resolution in task list endpoint. | **RESOLVED** |
| P0 | N+1 queries — process list | Batch progress, variables, and user-profile resolution in process list endpoint. | **RESOLVED** |
| P0 | N+1 queries — candidate users | Persist `candidateUsers` to DB instead of per-task `task_assignment_rule` query. | **RESOLVED** |
| P0 | Webhook/runtime timeouts | Configure `RestClient` connect and read timeouts globally; current default is infinite. | Open |
| P0 | Auth per-request overhead | Cache authorization groups, permissions, and super-admin checks; currently 3 remote calls per request. | Open |
| P1 | N+1 queries — timeline events | Batch task-instance and user-profile resolution in `ActivityInstanceService`. | **RESOLVED** |
| P1 | Statistics queries | Replace 6+ individual COUNT queries with grouped aggregate or cached result. | Open |
| P1 | Database indexes | Add Flyway-managed indexes for the most common task/process filters and sorts. | Open |
| P1 | Class-level @Transactional on consumers | Move `@Transactional` from class to method level on Kafka/RabbitMQ consumers. | Open |
| P1 | Unbounded archive queries | `archiveProcess`/`unArchiveProcess` load all instances without pagination and save individually. | Open |
| P1 | IAM profile sync filter | DB read/write on every authenticated request without caching. | Open |
| P2 | Page size validation | Add max limits for `page`, `size`, variable filters, and string query params. | Open |
| P2 | JSONB filtering | Limit dynamic JSONB variable searches; add expression indexes for high-value paths. | Open |
| P2 | N+1 queries — deployment list | Batch-load candidate starter groups instead of per-deployment query. | **RESOLVED** |
| P2 | Email sending | `mailSender.send()` blocks process-engine thread; consider async. | Open |
| P2 | Event publishing | `applicationEventPublisher.publishEvent()` is synchronous by default. | Open |
| P2 | Cache usage | Apply `@Cacheable` selectively; Caffeine is configured but no caching annotations exist. | Open |
| P2 | Connection pool | Tune HikariCP and PostgreSQL limits per pod replica count. | Open |
| P2 | Process list sorting | `ProcessInstanceRepositoryImpl#findAll` has no deterministic sort order. | Open |
| P2 | Kafka consumer config | No explicit concurrency, max-poll-records, or DLQ settings. | Open |
| P3 | N+1 queries — runtime priorities | Per-task `setTaskPriority()` during task creation; low impact (1-3 tasks typically). | Open |
| P3 | Audit/history retention | Review Activiti `history-level=full` and Envers retention for production volume. | Open |
| P3 | Build/runtime | Pin image tags, set JVM container memory flags, right-size Docker memory limit. | Open |

## Hot Paths Observed

- Task search/list: `TaskInstancesController` → `ListTaskInstancesCommandHandler` → `TaskInstanceService#getAllTaskInstances` → `TaskInstanceRepositoryImpl#findAll`.
- Process search/list: `ProcessInstanceService#getAllProcessInstances` → `ProcessInstanceRepositoryImpl#findAll`.
- Task statistics: `TaskInstanceRepositoryImpl#getGlobalTaskStatistics` and `getTaskStatisticsByUser`.
- Process statistics: `ProcessInstanceRepositoryImpl#getProcessInstanceStatistics`.
- Webhooks: `IgrpWebhookDelegate` and `IgrpProcessWebhookDelegate`.
- Message consumers: `AbstractStartProcessConsumer` and `AbstractProcessEventConsumer`.
- Auth chain: `SecurityConfig#jwtAuthenticationConverter` → `IAuthorizationServiceAdapter` (3 remote calls per request).
- Profile sync: `IAMUserProfileSyncFilter#doFilterInternal` (DB query per request).

---

## N+1 Query Patterns (P0)

### TaskInstanceService.getAllTaskInstances — per-task runtime calls — RESOLVED

**File:** `TaskInstanceService.java:247-278`

**Resolved on 2026-06-08.** Previously, the service looped through each task to call `getProcessVariables()` individually and resolved user profiles per task and per event. This caused ~250 external calls for a page of 50 tasks.

**What was done:**

- Process variables now use `runtimeProcessEngineRepository.getProcessVariablesBatch()` — a single batch call per page.
- User profiles now use `resolveAllUserProfiles()` — collects all user identifiers (startedBy, endedBy, assignedBy, event performedBy) across the entire page and resolves in a single `findBySubjectOrEmails` call.

All per-task queries in this loop have been eliminated.

### ProcessInstanceService.getAllProcessInstances — three calls per instance — RESOLVED

**File:** `ProcessInstanceService.java:47-86`

**Resolved on 2026-06-08.** Previously, the service called `setProcessInstanceProgress()`, `addProcessVariables()`, and `resolveUserProfiles()` individually per instance (150 calls for 50 instances).

**What was done:**

- Progress uses `runtimeProcessEngineRepository.getProcessInstanceTaskStatusBatch()` — collects all engine numbers, single batch call, distributes results.
- Variables use `runtimeProcessEngineRepository.getProcessVariablesBatch()` — same pattern as task list.
- User profiles use `resolveAllUserProfiles()` — collects all user identifiers across the page and resolves in a single `findBySubjectOrEmails` call.

### ProcessDeploymentService.getAllDeployments — per-deployment group query — RESOLVED

**File:** `ProcessDeploymentService.java:57-70`

**Resolved on 2026-06-08.** Previously called `getCandidateStarterGroups()` per deployment.

**What was done:**

- Uses `processDeploymentRepository.getCandidateStarterGroupsBatch()` — collects all deployment IDs, single batch call, distributes results. Note: the batch implementation currently loops internally in the repository since the framework adapter does not support a native batch API. The loop is contained in the infrastructure layer.

### TaskInstanceService.resolveCandidateUsers — per-task assignment rule query — RESOLVED

**File:** `TaskInstanceService.java` (previously lines 272, 594-605)

**Resolved on 2026-06-08.** Previously, `candidateUsers` was not persisted on `TaskInstanceEntity`. The only way to populate it was by querying `task_assignment_rule` per task inside the `getAllTaskInstances` loop (50 DB queries per page) and again in `getTaskById`.

**What was done:**

- Added `candidateUsers` `@ElementCollection` to `TaskInstanceEntity` with a new `t_task_instance_candidate_user` join table (Flyway V4 migration), mirroring the existing `candidateGroups` pattern.
- `TaskInstance.addCandidates()` now merges candidate users (via `mergeCandidateUsers()`) in addition to candidate groups, so users are stored on the domain model during task assignment.
- `TaskInstanceMapper` maps `candidateUsers` bidirectionally: `toModel()` loads from entity, `toNewTaskEntity()`/`toTaskEntity()` sync back via `syncCandidateUsers()`.
- Removed the per-task `resolveCandidateUsers()` calls from both `getAllTaskInstances()` and `getTaskById()`, along with the private method itself.
- Updated `candidateUserRulePredicate` in `TaskInstanceRepositoryImpl` to query from the entity's `candidateUsers` collection instead of `TaskAssignmentRuleEntity`, matching the `candidateGroupExistsPredicate` pattern.

### ActivityInstanceService.getProcessTimelineEvents — per-event queries — RESOLVED

**File:** `ActivityInstanceService.java:77-102`

**Resolved on 2026-06-08.** Previously had two N+1 patterns: per-event `findByExternalId()` task lookup and per-event `resolveUserProfiles()`.

**What was done:**

- Task lookup uses `taskInstanceRepository.findAllByExternalIds()` — collects all task IDs, single `WHERE externalId IN (...)` query, distributes results via map.
- User profiles use `resolveAllUserProfiles()` — collects all assignee identifiers, single `findBySubjectOrEmails` call, distributes via lookup map.

### TaskInstanceService.updateRuntimePriorities — per-task priority update (P3)

**File:** `TaskInstanceService.java:625-631`

```java
tasks.forEach(task ->
    runtimeProcessEngineRepository.setTaskPriority(
        task.getExternalId().getValue(),
        processInstance.getPriority()
    )
);
```

Per-task `setTaskPriority()` call to the runtime engine. Low impact — only called during task creation (not a list endpoint), and typically involves 1-3 tasks.

Recommended actions:

- If the runtime engine supports batch priority updates, add a batch method. Otherwise, accept the current pattern.

### TaskInstanceService.registerAssignmentRules — individual saves in loop

**File:** `TaskInstanceService.java:145-168`

Each assignment rule is saved individually via `taskAssignmentRuleRepository.save()` inside a `forEach` loop.

Recommended actions:

- Use `saveAll(List<TaskAssignmentRule>)` for batch inserts.
- Configure `spring.jpa.properties.hibernate.jdbc.batch_size=20` to enable JDBC batching.

---

## RestClient Timeouts (P0)

### RestClientConfig — no timeout defaults

**File:** `RestClientConfig.java:13-15`

```java
public RestClient defaultRestClient(RestClient.Builder builder) {
    return builder.build();  // infinite timeouts
}
```

### IgrpWebhookDelegate — blocks on external call indefinitely

**File:** `IgrpWebhookDelegate.java:104-140`

All four HTTP methods (GET, POST, PUT, DELETE) call `.retrieve().toEntity(String.class)` without any timeout. A slow or unresponsive external service will hold the process-engine worker thread indefinitely.

### IgrpProcessWebhookDelegate — same issue

**File:** `IgrpProcessWebhookDelegate.java:85-90`

Recommended actions:

- Set global defaults in `RestClientConfig`:

```java
@Bean
public RestClient defaultRestClient(RestClient.Builder builder) {
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    return builder.requestFactory(requestFactory).build();
}
```

- Add circuit breakers or bulkheads for webhook-heavy processes.
- Store failed webhook events for replay instead of blocking indefinitely.
- Add response size limits to prevent memory issues with large webhook responses.

---

## Authentication Per-Request Overhead (P0)

### SecurityConfig.jwtAuthenticationConverter — 3 remote calls per request

**File:** `SecurityConfig.java:127-148`

Every authenticated request triggers three calls to `authorizationService`:

```java
authorizationService.getActiveGroups(token, request);   // remote call 1
authorizationService.getPermissions(token, request);     // remote call 2
authorizationService.isSuperAdmin(token, request);       // remote call 3
```

Impact: At 100 req/s, this generates 300 authorization service calls per second. Any latency in the auth service directly multiplies request latency.

### IAMUserProfileSyncFilter — DB query on every request

**File:** `IAMUserProfileSyncFilter.java:47-54`

Every authenticated request calls `userProfileRepository.findBySubjectOrEmail(sub, email)` and potentially writes an update. This adds at least one DB round-trip to every request.

Recommended actions:

- Cache authorization results keyed by token hash with TTL matching token expiration (or a shorter TTL like 2-5 minutes). The Caffeine cache is already configured but unused.
- Cache user profile sync state per subject in-memory; only hit DB if the subject hasn't been seen in the current TTL window.
- Consider extracting groups/permissions from JWT claims if the identity provider supports it, eliminating remote calls entirely.

---

## Statistics Queries (P1)

### Task statistics — 6 separate COUNT queries

**File:** `TaskInstanceRepositoryImpl.java:328-345`

```java
long total = taskInstanceEntityRepository.count();
long available = countBySpec(statusSpec(TaskInstanceStatus.CREATED));
long assigned = countBySpec(statusSpec(TaskInstanceStatus.ASSIGNED));
long suspended = countBySpec(statusSpec(TaskInstanceStatus.SUSPENDED));
long completed = countBySpec(statusSpec(TaskInstanceStatus.COMPLETED));
long canceled = countBySpec(statusSpec(TaskInstanceStatus.CANCELED));
```

### Per-user task statistics — 6+ COUNT queries

**File:** `TaskInstanceRepositoryImpl.java:349-390`

Same pattern but with additional visibility predicates per query.

### Process statistics — 6 COUNT queries

**File:** `ProcessInstanceRepositoryImpl.java:141-163`

Same pattern: `total` + 5 status-specific counts.

Impact: Each statistics endpoint fires 6+ DB queries. These endpoints are often polled frequently for dashboards.

Recommended actions:

- Replace with a single query using `GROUP BY status`:

```sql
SELECT status, COUNT(*) FROM t_task_instance GROUP BY status;
```

- Or cache statistics with a short TTL (30-60 seconds) via `@Cacheable("taskStatistics")`.

---

## Database And Query Performance (P1)

### Add indexes for common filters and sorts

`TaskInstanceRepositoryImpl#findAll` filters by process instance, process number/business key, application base, task name, process name, release key, status, date range, current-user visibility, candidate groups, archived state, and sorts by `startedAt DESC`.

Existing indexes from migrations:

- `V2__create_task_instance_candidate_group.sql` — candidate group join table.
- `V3__create_task_assignment_rule.sql` — task assignment rule indexes.

Missing indexes for hot query paths:

```sql
-- Task instance queries
CREATE INDEX IF NOT EXISTS idx_task_instance_process_instance_id
  ON t_task_instance (process_instance_id);

CREATE INDEX IF NOT EXISTS idx_task_instance_status_started_at
  ON t_task_instance (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_instance_started_at
  ON t_task_instance (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_instance_assigned_by_status
  ON t_task_instance (assigned_by, status);

CREATE INDEX IF NOT EXISTS idx_task_instance_ended_by_status
  ON t_task_instance (ended_by, status);

CREATE INDEX IF NOT EXISTS idx_task_instance_task_key
  ON t_task_instance (task_key);

-- Process instance queries
CREATE INDEX IF NOT EXISTS idx_process_instance_status_started
  ON t_process_instance (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_process_instance_archived_started
  ON t_process_instance (is_archived, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_process_instance_release
  ON t_process_instance (proc_release_id);

CREATE INDEX IF NOT EXISTS idx_process_instance_release_key
  ON t_process_instance (proc_release_key);

CREATE INDEX IF NOT EXISTS idx_process_instance_application_base
  ON t_process_instance (application_base);

CREATE INDEX IF NOT EXISTS idx_process_instance_number
  ON t_process_instance (number);

CREATE INDEX IF NOT EXISTS idx_process_instance_business_key
  ON t_process_instance (business_key);

-- Combined index for task assignment rule lookups
CREATE INDEX IF NOT EXISTS idx_task_assignment_rule_process_task
  ON t_task_assignment_rule (process_instance_id, task_definition_key);
```

Notes:

- Confirm table/column names against the generated schema before creating migrations.
- Use `EXPLAIN (ANALYZE, BUFFERS)` on real search queries before and after each index.
- Avoid adding all indexes blindly. Each write-heavy workflow pays for every index.

### Watch dynamic JSONB filters

**File:** `TaskInstanceRepositoryImpl.java:204-324`

Task variable filters use dynamic `jsonb_extract_path_text` and conversions like `to_number` / `to_date`. These predicates bypass indexes and become sequential scans on large tables.

Recommended actions:

- Limit the number of variable expressions per request.
- Validate variable names to simple path segments, for example `customer.nif`, not arbitrary text.
- Track the most common variable filters in metrics.
- Add expression indexes for high-value stable variables only.
- For heavy reporting, consider a normalized projection table.

### Add stable process list sorting

**File:** `ProcessInstanceRepositoryImpl.java:54`

```java
PageRequest pageRequest = PageRequest.of(filter.getPage(), filter.getSize());
```

No explicit sort. This causes unstable pagination when rows are inserted or updated between page requests.

Recommended actions:

- Sort process listings by `startedAt DESC, id DESC` or another deterministic business order.
- Match indexes to that order.

---

## Kafka/RabbitMQ Consumer Issues (P1)

### Class-level @Transactional holds DB connections during message processing

**File:** `KafkaProcessEventConsumer.java:14`

```java
@Component
@Transactional              // Class-level: all methods are transactional
public class KafkaProcessEventConsumer extends AbstractProcessEventConsumer {
```

The `@KafkaListener` method inherits `@Transactional`, opening a database transaction for the entire message processing duration. If `handleMessage()` triggers a webhook delegate (no timeout), the transaction and database connection are held indefinitely.

The same pattern exists in:
- `KafkaStartProcessConsumer`
- `RabbitProcessEventConsumer`
- `RabbitStartProcessConsumer`

Recommended actions:

- Move `@Transactional` from class level to method level with narrower scope.
- Separate webhook/external calls from transactional DB operations.
- Add consumer concurrency and max-poll-records configuration:

```properties
spring.kafka.listener.concurrency=3
spring.kafka.consumer.max-poll-records=10
spring.kafka.listener.poll-timeout=3000
```

- Configure DLQ for poison messages.
- Review `enable-auto-commit=true` if at-least-once semantics matter.

### Per-message JWT decoding

**File:** `AbstractStartProcessConsumer.java:68`, `AbstractProcessEventConsumer.java`

Every message triggers `jwtDecoder.decode(jwt)` which may perform remote JWKS validation. No caching of validated tokens.

Recommended actions:

- Cache decoded/validated JWTs by token hash with short TTL.
- Or use local JWT signature verification if the issuer public key is cached.

---

## Unbounded Archive/Unarchive Queries (P1)

### ProcessDeploymentService.archiveProcess / unArchiveProcess

**File:** `ProcessDeploymentService.java:250-276`

```java
List<ProcessInstance> processInstances = processInstanceRepository.findAllByProcessReleaseId(processDefinitionId);
for (ProcessInstance processInstance : processInstances) {
    processInstance.archive();
    processInstanceRepository.save(processInstance);  // Individual save per instance
}
```

Issues:

1. Loads ALL instances into memory at once (no pagination, no LIMIT).
2. Saves each instance individually in a loop.
3. Can cause OOM for process definitions with thousands of instances.

Recommended actions:

- Use paginated queries: `findAllByProcessReleaseId(id, Pageable)`.
- Use `saveAll()` for batch persistence.
- Or use a single bulk UPDATE query: `UPDATE t_process_instance SET is_archived = true WHERE proc_release_id = ?`.

---

## API And Pagination (P2)

### Cap page sizes and reject invalid values

`TaskInstanceFilter` defaults to size `50`, but the controllers accept request `size` values without a visible max. A user can request very large pages and force expensive DB work, runtime calls, JSON conversion, and DTO mapping.

Recommended actions:

- Enforce a maximum page size (e.g. `100` or `200`) in mapper/filter construction.
- Reject negative `page` and non-positive `size`.
- Add `@Min`, `@Max`, and typed request models where possible.
- Apply the same rule to process, area, deployment, and user-profile list filters.

### Add a lightweight list response mode

Task list DTOs currently include variables/forms/process variables. That can make list responses heavy, especially when many tasks contain large JSON variables.

Recommended actions:

- Keep list endpoints summary-focused by default.
- Load variables/forms only on detail endpoints or behind an explicit `includeVariables=true` flag.
- Exclude task events from list responses unless explicitly requested.

---

## Synchronous Email Sending (P2)

### SendEmailDelegate — blocks process-engine thread

**File:** `SendEmailDelegate.java:61`

```java
mailSender.send(message);
```

This is a blocking SMTP call inside a process-engine delegate. If the SMTP server is slow, it stalls the entire process instance execution.

Recommended actions:

- Send emails asynchronously via a Kafka topic or Spring `@Async`.
- Or use a separate thread pool for email sending.
- Add SMTP timeout configuration.

---

## Synchronous Event Publishing (P2)

Spring's `applicationEventPublisher.publishEvent()` is synchronous by default. Any slow `@EventListener` blocks the calling thread (often a process-engine worker or request thread).

Recommended actions:

- Configure async event execution with `@EnableAsync` and a dedicated task executor.
- Use `@TransactionalEventListener(phase = AFTER_COMMIT)` for listeners that don't need to participate in the current transaction.

---

## Cache Strategy (P2)

The project enables Spring Cache and configures Caffeine (`maximumSize=10000, expireAfterWrite=5m`), but no `@Cacheable` usage was found in `src/main/java`.

High-value candidates:

- **Authorization groups/permissions** — cache by token hash, TTL 2-5 minutes. This would eliminate 3 remote calls per request.
- **IAM user profiles** — cache by subject/email, TTL 5 minutes. Eliminates per-request DB queries from `IAMUserProfileSyncFilter`.
- **Process artifact metadata** by release ID, with invalidation on deploy/import/archive.
- **Statistics results** with short TTL (30-60 seconds).

Consider per-cache TTL configuration instead of a single global spec:

```java
@Bean
public CacheManager cacheManager() {
    var builder = Caffeine.newBuilder();
    var shortTtl = builder.maximumSize(5000).expireAfterWrite(Duration.ofMinutes(1)).build();
    var mediumTtl = builder.maximumSize(10000).expireAfterWrite(Duration.ofMinutes(5)).build();
    // ...
}
```

Avoid caching:

- Per-request authorization decisions without strict user/token scoping.
- Task/process details that change during workflow transitions.
- Runtime variables unless staleness is acceptable.

---

## Connection Pool And PostgreSQL (P2)

No explicit HikariCP pool settings were observed in the properties.

Recommended actions:

- Set `spring.datasource.hikari.maximum-pool-size` per pod based on DB capacity and replica count.
- Set `connection-timeout`, `idle-timeout`, and `max-lifetime`.
- Enable leak detection temporarily during performance testing.
- Monitor active/idle connections, wait time, slow queries, deadlocks, and locks.
- Pay special attention to connection exhaustion risk from class-level `@Transactional` on message consumers.

---

## Audit, History, And Retention (P3)

The service enables Activiti full history and Envers auditing with `global_with_modified_flag=true` and `track_entities_changed_in_revision=true`. This creates significant write amplification on every data modification.

Recommended actions:

- Confirm whether production needs `spring.activiti.history-level=full` for every process.
- Evaluate if `global_with_modified_flag=true` is necessary; it adds `*_mod` boolean columns to every audit table.
- Add retention or archival policy for completed/canceled process data.
- Partition large audit/history tables by time if volume is high.
- Monitor table bloat and autovacuum behavior in PostgreSQL.

---

## Observability (P2-P3)

OpenTelemetry and actuator Prometheus are present, which is a strong foundation.

Recommended actions:

- Track endpoint latency, DB query latency, runtime engine latency, webhook latency, and Kafka consumer lag.
- Add custom timers around task list enrichment and process-engine calls.
- Use trace sampling in production to control overhead.
- Alert on p95/p99 latency, DB pool exhaustion, webhook failures, and message retry/DLQ growth.

---

## Docker And Deployment (P3)

The Dockerfile uses Chainguard images and a multi-stage build, which is a good baseline.

Recommended actions:

- Pin image versions or digests instead of `latest`.
- Run tests in CI before image packaging.
- Set JVM memory flags: `-XX:MaxRAMPercentage=75`.
- Docker memory limit of 700MB may be tight for JVM + Activiti + Hibernate + Kafka client + cache; consider 1024MB+.
- Add Kubernetes readiness/liveness probes so slow pods are removed safely.
- Align Kubernetes memory limits with heap, native memory, and process-engine workload.

---

## Suggested Next Steps

1. **Week 1 — Highest impact, lowest risk:**
   - Add `RestClient` connect/read timeouts globally (blocks indefinite hangs).
   - Add missing database indexes via Flyway migration.
   - Cache authorization calls in `SecurityConfig` (eliminates 3 remote calls/request).

2. **Week 2 — N+1 query fixes:**
   - ~~Batch process variable loading in `TaskInstanceService#getAllTaskInstances`.~~ **DONE.**
   - ~~Batch user-profile resolution across full page in both task and process list flows.~~ **DONE.**
   - ~~Batch process progress + variables in `ProcessInstanceService#getAllProcessInstances`.~~ **DONE.**
   - ~~Batch timeline event enrichment in `ActivityInstanceService#getProcessTimelineEvents`.~~ **DONE.**
   - ~~Batch candidate starter groups in `ProcessDeploymentService#getAllDeployments`.~~ **DONE.**
   - ~~Persist `candidateUsers` to task entity to eliminate per-task `resolveCandidateUsers` N+1 query.~~ **DONE.**

3. **Week 3 — Consumer and statistics fixes:**
   - Move `@Transactional` to method level on Kafka/RabbitMQ consumers.
   - Replace statistics 6-query pattern with `GROUP BY` or cached aggregate.
   - Add Kafka consumer concurrency and DLQ configuration.

4. **Week 4 — Cleanup and hardening:**
   - Add page size validation and max limits on all list endpoints.
   - Make email sending asynchronous.
   - Paginate or batch-update `archiveProcess`/`unArchiveProcess`.
   - Configure per-cache TTL policies.
   - Decide retention policy for Activiti history and Envers audit tables.
