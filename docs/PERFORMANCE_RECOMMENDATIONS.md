# Performance Recommendations

Reviewed on 2026-05-23 for the iGRP Platform Process Management API.

This project is a Spring Boot 3 / Java 21 service with JPA/PostgreSQL, Activiti runtime integration, Kafka/RabbitMQ delegates, Caffeine cache dependencies, Flyway, Envers auditing, OpenTelemetry, Docker, and Kubernetes manifests.

## Priority Summary

| Priority | Area | Recommendation |
| --- | --- | --- |
| P1 | API request bounds | Add validation and max limits for `page`, `size`, variable filters, and string query params. |
| P1 | Task list enrichment | Batch or cache runtime variable and user-profile enrichment in task listing flows. |
| P1 | Database indexes | Add Flyway-managed indexes for the most common task/process filters and sorts. |
| P1 | Webhook/runtime calls | Configure `RestClient` timeouts, retries, and failure handling. |
| P1 | Audit/history storage | Review Activiti `history-level=full` and Envers retention for production volume. |
| P2 | Statistics endpoints | Replace many independent count queries with grouped aggregate queries or short TTL cache. |
| P2 | JSONB filtering | Limit dynamic JSONB searches and add expression indexes or projection tables for common variables. |
| P2 | Cache usage | Apply `@Cacheable` selectively where data is stable and scoped safely. |
| P2 | Connection pool | Tune HikariCP and PostgreSQL limits per pod replica count. |
| P3 | Build/runtime | Pin image tags, run tests in image builds or CI, and set JVM container memory defaults. |

## Hot Paths Observed

- Task search/list: `TaskInstancesController` -> `ListTaskInstancesCommandHandler` / `GetAllMyTasksCommandHandler` -> `TaskInstanceService#getAllTaskInstances` -> `TaskInstanceRepositoryImpl#findAll`.
- Process search/list: `ProcessInstanceRepositoryImpl#findAll`.
- Task statistics: `TaskInstanceRepositoryImpl#getGlobalTaskStatistics` and `getTaskStatisticsByUser`.
- Process statistics: `ProcessInstanceRepositoryImpl#getProcessInstanceStatistics`.
- Webhooks: `IgrpWebhookDelegate` and `IgrpProcessWebhookDelegate`.
- Message consumers: `AbstractStartProcessConsumer` and `AbstractProcessEventConsumer`.

## API And Pagination

### Cap page sizes and reject invalid values

`TaskInstanceFilter` defaults to size `50`, but the controllers accept request `size` values without a visible max. A user can request very large pages and force expensive DB work, runtime calls, JSON conversion, and DTO mapping.

Recommended actions:

- Enforce a maximum page size, for example `100` or `200`, in mapper/filter construction.
- Reject negative `page` and non-positive `size`.
- Add `@Min`, `@Max`, and typed request models where possible instead of raw query params.
- Apply the same rule to process, area, deployment, and user-profile list filters.
- Log rejected values at debug or warn without echoing sensitive payloads.

### Add a lightweight list response mode

Task list DTOs currently include variables/forms/process variables. That can make list responses heavy, especially when many tasks contain JSON variables.

Recommended actions:

- Keep list endpoints summary-focused by default.
- Load variables/forms only on detail endpoints or behind an explicit `includeVariables=true` flag with strict size limits.
- Add response-size metrics for task and process list endpoints.

## Database And Query Performance

### Add indexes for common filters and sorts

`TaskInstanceRepositoryImpl#findAll` filters by process instance, process number/business key, application base, task name, process name, release key, status, date range, current-user visibility, candidate groups, archived state, and sorts by `startedAt desc`.

Candidate group indexing was already started in `V2__create_task_instance_candidate_group.sql`, which is good. Add the rest through Flyway after checking the existing production schema.

Recommended indexes to evaluate:

```sql
CREATE INDEX IF NOT EXISTS idx_task_instance_started_at
  ON t_task_instance (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_instance_status_started_at
  ON t_task_instance (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_instance_process_instance
  ON t_task_instance (process_instance_id);

CREATE INDEX IF NOT EXISTS idx_task_instance_assigned_by_status
  ON t_task_instance (assigned_by, status);

CREATE INDEX IF NOT EXISTS idx_task_instance_ended_by_status
  ON t_task_instance (ended_by, status);

CREATE INDEX IF NOT EXISTS idx_process_instance_archived_started
  ON t_process_instance (is_archived, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_process_instance_status_started
  ON t_process_instance (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_process_instance_release
  ON t_process_instance (proc_release_id, proc_release_key);

CREATE INDEX IF NOT EXISTS idx_process_instance_application_base
  ON t_process_instance (application_base);

CREATE INDEX IF NOT EXISTS idx_process_instance_number
  ON t_process_instance (number);

CREATE INDEX IF NOT EXISTS idx_process_instance_business_key
  ON t_process_instance (business_key);
```

Notes:

- Confirm table/column names against the generated schema before creating migrations.
- Use `EXPLAIN (ANALYZE, BUFFERS)` on real search queries before and after each index.
- Avoid adding all indexes blindly. Each write-heavy workflow pays for every index.

### Watch dynamic JSONB filters

Task variable filters use dynamic `jsonb_extract_path_text` and conversions like `to_number` / `to_date`. These predicates are flexible but can become sequential scans on large task tables.

Recommended actions:

- Limit the number of variable expressions per request.
- Validate variable names to simple path segments, for example `customer.nif`, not arbitrary text.
- Track the most common variable filters in metrics.
- Add expression indexes for high-value stable variables only.
- For heavy reporting/search use cases, consider a normalized projection table such as `t_task_variable_index(task_instance_id, name, type, value_text, value_number, value_date)`.

### Use fetch plans for list mapping

`TaskInstanceMapper#toModel` reads fields from the related process instance for every task. With lazy relationships, this can become an N+1 query pattern depending on the JPA session and fetch plan.

Recommended actions:

- Add an `@EntityGraph` or repository method with fetch join for task list queries that need process instance fields.
- Keep event collections out of list fetches unless explicitly requested.
- Add integration tests or SQL logging in a controlled profile to confirm query count.

### Add stable process list sorting

`ProcessInstanceRepositoryImpl#findAll` creates `PageRequest.of(filter.getPage(), filter.getSize())` without an explicit sort. That can cause unstable pagination when rows are inserted or updated.

Recommended actions:

- Sort process listings by `startedAt DESC, id DESC` or another deterministic business order.
- Match indexes to that order.

## Runtime And External Calls

### Batch task list enrichment

After loading a page of tasks, `TaskInstanceService#getAllTaskInstances` enriches each page with runtime process variables and user profiles. Runtime variables are de-duplicated by engine process number, which is good, but still one call per unique process number. User profiles are resolved per task and event.

Recommended actions:

- Add a runtime-process bulk fetch API if the engine supports it.
- Add short TTL caching for runtime variables when freshness allows it.
- Resolve all user identifiers for the page in a single `findBySubjectOrEmails` call.
- Avoid event user-profile resolution on list responses unless events are included.
- Add metrics for number of runtime calls per list request.

### Configure RestClient timeouts

`RestClientConfig` and `IgrpRestClientConfig` build a default `RestClient` without explicit connect/read timeouts. Webhook delegates can hold process-engine worker threads if remote services are slow.

Recommended actions:

- Configure connect and read timeouts.
- Use retries only for idempotent calls or with idempotency keys.
- Add circuit breakers or bulkheads for webhook-heavy processes.
- Store failed webhook events for replay instead of blocking indefinitely.

### Tune message producers and consumers

Kafka producer sends are fire-and-forget in `KafkaSender`. Consumers run process operations inside listener flow.

Recommended actions:

- Observe send failures and record callback results.
- Tune consumer concurrency only after DB and process-engine capacity are known.
- Use bounded retry/DLQ for poison messages.
- Review `enable-auto-commit=true` if exactly-once or at-least-once behavior matters.

## Cache Strategy

The project enables Spring Cache and configures Caffeine, but no `@Cacheable` usage was found in `src/main/java`.

Good candidates:

- Static config parameter lists such as status/event-type values.
- Process artifact metadata by release id, with invalidation on deploy/import/archive.
- Authorization lookups, only with very short TTL and clear tenant/user scoping.
- IAM user profile lookups by subject/email.

Avoid caching:

- Per-request authorization decisions without strict user/token scoping.
- Task/process details that change during workflow transitions.
- Runtime variables unless staleness is acceptable.

## Audit, History, And Retention

The service enables Activiti full history and Envers auditing. That is useful for process traceability but can grow tables quickly.

Recommended actions:

- Confirm whether production needs `spring.activiti.history-level=full` for every process.
- Add retention or archival policy for completed/canceled process data.
- Partition large audit/history tables by time if volume is high.
- Keep audit indexes aligned with investigations and support queries.
- Monitor table bloat and autovacuum behavior in PostgreSQL.

## Connection Pool And PostgreSQL

No explicit HikariCP pool settings were observed in the properties.

Recommended actions:

- Set `spring.datasource.hikari.maximum-pool-size` per pod based on DB capacity and replica count.
- Set `connection-timeout`, `idle-timeout`, and `max-lifetime`.
- Enable leak detection temporarily during performance testing.
- Monitor active/idle connections, wait time, slow queries, deadlocks, and locks.

## Observability

OpenTelemetry and actuator Prometheus are present, which is a strong foundation.

Recommended actions:

- Track endpoint latency, DB query latency, runtime engine latency, webhook latency, and Kafka consumer lag.
- Add custom timers around task list enrichment and process-engine calls.
- Use trace sampling in production to control overhead.
- Alert on p95/p99 latency, DB pool exhaustion, webhook failures, and message retry/DLQ growth.

## Docker And Deployment

The Dockerfile uses Chainguard images and a multi-stage build, which is a good baseline.

Recommended actions:

- Pin image versions or digests instead of `latest`.
- Run tests in CI before image packaging. Keep image build fast, but do not let tests disappear from the pipeline.
- Set JVM memory flags if needed, for example `-XX:MaxRAMPercentage=75`.
- Add Kubernetes readiness/liveness probes so slow pods are removed safely.
- Align Kubernetes memory limits with heap, native memory, and process-engine workload.

## Suggested Next Steps

1. Add request size validation and max page sizes.
2. Add SQL query-count tests or profiling for task/process list endpoints.
3. Create Flyway migrations for the most proven task/process indexes.
4. Add RestClient timeouts and webhook metrics.
5. Batch user-profile enrichment for task list responses.
6. Decide retention policy for Activiti history and Envers audit tables.
