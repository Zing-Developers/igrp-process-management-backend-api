# Filtering Tasks by Variables

This document describes how task instances (and, to a lesser extent, process instances)
can be filtered by **process variables** in the iGRP Process Management API.

It covers the public HTTP contract, supported operators and types, how the filter is
translated into a SQL query against the PostgreSQL `JSONB` column that stores task
variables, the interaction with user-visibility rules, and known limitations.

---

## 1. Overview

Clients can search tasks by one or more **variable expressions** — a triple of
`(name, operator, value)` — sent in the request body. A task matches if **either**
of two sources matches:

1. **Process variables** — resolved by the process engine. The service asks the
   engine which process instances satisfy the variable expressions
   (`getAllProcessInstancesByVariables`), collects their engine process numbers onto
   the filter, and the repository restricts tasks to those parent processes.
2. **Task-local variables** — the snapshot persisted on the `JSONB` `variables`
   column of `t_task_instance` (written when a task is completed/saved). The backend
   converts each expression into a JPA `Specification<TaskInstanceEntity>` predicate
   using PostgreSQL's `jsonb_extract_path_text` to read the value at a dotted path
   and compares it with the SQL operator for the value's Java type.

The two sources are combined with **logical OR**: a task surfaces if its parent
process variables match **or** its task-local variables match. Within each source,
the individual expressions are combined with **logical AND** (there is no OR / NOT
grouping of expressions in the public contract).

> Note: this OR combination was restored in this change. A prior refactor
> (`674e8fc`) had reduced task filtering to the task-local JSONB column only, which
> silently ignored process variables — the common case, since the JSONB column is
> empty until a task is completed.

Process instances can be filtered by variables too, with the filtering delegated to
the process engine (not the database) — see §8.

---

## 2. API Surface

### 2.1 Search task instances

```
POST /tasks-instances/search
Content-Type: application/json
```

Source: [TaskInstancesController.java:48](../src/main/java/cv/igrp/platform/process/management/processruntime/interfaces/rest/TaskInstancesController.java)

**Query parameters** (all optional):

| Parameter             | Type    | Notes                                                                 |
|-----------------------|---------|-----------------------------------------------------------------------|
| `processInstanceId`   | String  | Filter by parent process instance id.                                 |
| `processNumber`       | String  | Filter by parent process number.                                      |
| `processReleaseKey`   | String  | Filter by deployed process release key.                               |
| `applicationBase`     | String  | Tenant/application scoping.                                           |
| `candidateGroups`     | String  | Comma-separated list of groups the task is offered to.                |
| `user`                | String  | Assignee user code.                                                   |
| `status`              | String  | `CREATED`, `ASSIGNED`, `SUSPENDED`, `COMPLETED`, `CANCELED`.          |
| `dateFrom` / `dateTo` | String  | ISO date range on task creation.                                      |
| `page` / `size`       | Integer | Pagination (defaults: `page=0`, `size=50`).                           |
| `name`                | String  | Task name contains.                                                   |
| `processName`         | String  | Process name contains.                                                |
| `filterByCurrentUser` | boolean | When `true`, restricts results to tasks visible to the caller (§9).   |

**Body** — `VariablesFilterDTO`:

```json
{
  "variables": [
    { "name": "string", "operator": "EQUALS", "value": <any> }
  ]
}
```

- `name` is required (non-blank).
- `operator` is required (see §4).
- `value` is required.
- `variables` may be an empty array when no variable filtering is needed.

Source:
- [VariablesFilterDTO.java](../src/main/java/cv/igrp/platform/process/management/processruntime/application/dto/VariablesFilterDTO.java)
- [VariablesExpressionDTO.java](../src/main/java/cv/igrp/platform/process/management/processruntime/application/dto/VariablesExpressionDTO.java)

**Response** — `TaskInstanceListPageDTO` (paginated list of `TaskInstanceDTO`).

### 2.2 Related endpoints accepting the same body

| Endpoint                       | Purpose                                                       |
|--------------------------------|---------------------------------------------------------------|
| `POST /tasks-instances/search` | Generic search (see above).                                   |
| `POST /tasks-instances/me`     | "My tasks" — adds caller-visibility restrictions (§9).        |
| `POST /process-instances/search` | Process-instance search (variable filter goes to the engine, §8). |

---

## 3. Data Model

### 3.1 Task variables storage

Task variables live on `TaskInstanceEntity` as a JSONB column:

- Table: `t_task_instance`
- Column: `variables JSONB`
- Entity field:
  [TaskInstanceEntity.java:97](../src/main/java/cv/igrp/platform/process/management/shared/infrastructure/persistence/entity/TaskInstanceEntity.java)

Each row holds a JSON object such as:

```json
{
  "status": "ACTIVE",
  "priority": 7,
  "amount": 1250.50,
  "approved": true,
  "dueDate": "2026-05-01",
  "customer": { "id": "C-42", "city": "Praia" }
}
```

Nested objects are reachable via **dot notation** in the `name` field of a
variable expression (for example, `customer.city`).

### 3.2 Domain model

- [VariablesExpression.java](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/models/VariablesExpression.java)
  — immutable triple `(name, operator, value)` with `@NonNull` guarantees; `value`
  may be `Number`, `Boolean`, `LocalDate`, or `String`.
- [TaskInstanceFilter.java](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/models/TaskInstanceFilter.java)
  — filter object carrying variable expressions plus the query parameters listed in §2.1.
- [VaribalesOperator.java](../src/main/java/cv/igrp/platform/process/management/shared/application/constants/VaribalesOperator.java)
  — enum of supported operators (note the spelling as generated by iGRP Studio).

### 3.3 Process variables

Process instances do **not** store variables on the entity. Variable filtering on
processes is resolved by asking the process engine for the set of matching
process numbers, then constraining the main JPA query with that set —
see [ProcessInstanceService.java:45](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/service/ProcessInstanceService.java)
and [RuntimeProcessEngineRepositoryImpl.java:406](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/RuntimeProcessEngineRepositoryImpl.java).

The **task** search reuses this same engine resolution for its process-variable
half: `TaskInstanceService.getAllTaskInstances` calls
`runtimeProcessEngineRepository.getAllProcessInstancesByVariables(...)` and feeds the
matching engine process numbers into `TaskInstanceFilter.engineProcessNumbers`. The
repository then OR-combines an `engineProcessNumber IN (...)` predicate (process
variables) with the task-local JSONB predicate (§5.1). When the engine returns no
matches, the engine-number list stays empty and the result depends solely on the
task-local match.

---

## 4. Supported Operators

Values from `VaribalesOperator`
([source](../src/main/java/cv/igrp/platform/process/management/shared/application/constants/VaribalesOperator.java)):

| Operator                  | Purpose                      |
|---------------------------|------------------------------|
| `EQUALS`                  | `=`                          |
| `EQUALS_IGNORE_CASE`      | case-insensitive equality    |
| `NOT_EQUALS`              | `<>`                         |
| `NOT_EQUALS_IGNORE_CASE`  | case-insensitive inequality  |
| `GREATER_THAN`            | `>`                          |
| `GREATER_THAN_OR_EQUAL`   | `>=`                         |
| `LESS_THAN`               | `<`                          |
| `LESS_THAN_OR_EQUAL`      | `<=`                         |
| `LIKE`                    | `LIKE '%value%'`             |
| `LIKE_IGNORE_CASE`        | `LOWER(col) LIKE '%value%'`  |

---

## 5. Operator × Value-Type Matrix

The operator set actually allowed at runtime depends on the **Java type of `value`**
after Jackson deserialization. If an unsupported combination is used, the repository
throws `IllegalArgumentException: "Operator X not supported for TYPE"` from
[TaskInstanceRepositoryImpl.java:274](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java).

| Value type            | EQUALS | EQ_ICASE | NOT_EQ | NE_ICASE | GT / GTE / LT / LTE | LIKE | LIKE_ICASE |
|-----------------------|:------:|:--------:|:------:|:--------:|:-------------------:|:----:|:----------:|
| `Number` (int/double) |   ✅   |    ❌    |   ✅   |    ❌    |          ✅         |  ❌  |     ❌     |
| `LocalDate`           |   ✅   |    ❌    |   ✅   |    ❌    |          ✅         |  ❌  |     ❌     |
| `Boolean`             |   ✅   |    ❌    |   ✅   |    ❌    |          ❌         |  ❌  |     ❌     |
| `String` (default)    |   ✅   |    ✅    |   ✅   |    ✅    |          ❌         |  ✅  |     ✅     |

### 5.1 Type-specific SQL

Generated predicates (simplified):

- **Number** — `to_number(jsonb_extract_path_text(variables, ...), '999999999.999999') <op> :value`
- **Date** — `to_date(jsonb_extract_path_text(variables, ...), 'YYYY-MM-DD') <op> :value`
- **Boolean** — `jsonb_extract_path_text(variables, ...) = 'true'|'false'`
- **String** — `jsonb_extract_path_text(variables, ...) <op> :value` (LIKE wraps the value with `%…%`)

Source: [TaskInstanceRepositoryImpl.java:274-357](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java).

---

## 6. Request/Response Shapes

### 6.1 Variable expression

```json
{
  "name": "customer.city",
  "operator": "LIKE_IGNORE_CASE",
  "value": "praia"
}
```

- `name` — dotted JSON path into the `variables` object. Each segment becomes one
  call to `jsonb_extract_path_text`.
- `operator` — one of the enum values in §4.
- `value` — the literal to compare. The **JSON type** determines the SQL branch
  used at query time (integer → Number, `"2026-05-01"` → String unless the caller's
  DTO typing coerces it to `LocalDate`, etc.).

### 6.2 Filter payload

```json
{
  "variables": [
    { "name": "status",       "operator": "EQUALS",           "value": "PENDING" },
    { "name": "priority",     "operator": "GREATER_THAN",     "value": 5 },
    { "name": "customer.age", "operator": "LESS_THAN_OR_EQUAL", "value": 65 }
  ]
}
```

All three expressions are ANDed together.

### 6.3 Response (excerpt)

```json
{
  "total": 1,
  "page": 0,
  "size": 50,
  "content": [
    {
      "id": "…",
      "name": "Review application",
      "status": "CREATED",
      "assignedBy": "…",
      "candidateGroups": "managers,reviewers",
      "processNumber": "PRC-000123",
      "variables": { "status": "PENDING", "priority": 7, "customer": { "age": 41 } }
    }
  ]
}
```

---

## 7. Examples

### 7.1 Simple equality on a top-level string

```http
POST /tasks-instances/search?page=0&size=10
Content-Type: application/json

{
  "variables": [
    { "name": "status", "operator": "EQUALS", "value": "ACTIVE" }
  ]
}
```

### 7.2 Numeric comparison

```http
POST /tasks-instances/search?page=0&size=20

{
  "variables": [
    { "name": "priority", "operator": "GREATER_THAN", "value": 5 }
  ]
}
```

### 7.3 Nested path + case-insensitive contains

```http
POST /tasks-instances/search?applicationBase=myapp

{
  "variables": [
    { "name": "customer.city", "operator": "LIKE_IGNORE_CASE", "value": "pra" }
  ]
}
```

### 7.4 Multiple conditions (AND)

```http
POST /tasks-instances/search?status=CREATED

{
  "variables": [
    { "name": "status",   "operator": "EQUALS",       "value": "PENDING" },
    { "name": "priority", "operator": "GREATER_THAN", "value": 5 }
  ]
}
```

### 7.5 Boolean flag

```http
POST /tasks-instances/search

{
  "variables": [
    { "name": "approved", "operator": "EQUALS", "value": true }
  ]
}
```

### 7.6 Process-instance search by a variable

```http
POST /process-instances/search?applicationBase=myapp

{
  "variables": [
    { "name": "department", "operator": "EQUALS", "value": "HR" }
  ]
}
```

---

## 8. End-to-End Flow

### 8.1 Tasks (hybrid: engine + database)

```
TaskInstancesController.listTaskInstances             (controller)
   └─> ListTaskInstancesCommandHandler                (CQRS handler)
        └─> TaskInstanceService.getAllTaskInstances   (visibility rules)
             ├─> runtimeProcessEngineRepository.getAllProcessInstancesByVariables
             │       └─> filter.includeEngineProcessNumber(...)  (process-variable matches)
             └─> TaskInstanceRepositoryImpl.findAll   (JPA Specification)
                  └─> buildSpecification               (compose predicates)
                       └─> cb.or(                       (a task matches if EITHER side matches)
                            engineProcessNumber IN (...) ,   // process variables
                            cb.and(buildVariablePredicate(expr) ...)  // task-local JSONB
                          )
                            ├─> buildJsonPathExpression  (JSONB path)
                            └─> buildOperatorPredicate   (type-aware operator)
```

Key references:

- Controller — [TaskInstancesController.java:68](../src/main/java/cv/igrp/platform/process/management/processruntime/interfaces/rest/TaskInstancesController.java)
- Handler — [ListTaskInstancesCommandHandler.java](../src/main/java/cv/igrp/platform/process/management/processruntime/application/commands/ListTaskInstancesCommandHandler.java)
- Mapping DTO → domain — [TaskInstanceMapper.java:284](../src/main/java/cv/igrp/platform/process/management/processruntime/mappers/TaskInstanceMapper.java)
- Service — [TaskInstanceService.java:186](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/service/TaskInstanceService.java)
- Repository — [TaskInstanceRepositoryImpl.java:79](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java)
- Variable predicate — [TaskInstanceRepositoryImpl.java:237](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java)

### 8.2 Processes (engine-delegated)

```
ProcessInstanceController.listProcessInstances
   └─> ListProcessInstancesCommandHandler
        └─> ProcessInstanceService.getAllProcessInstances
             ├─> runtimeProcessEngineRepository.getAllProcessInstancesByVariables(vars)
             │       └─> returns Set<processNumber> from the engine
             └─> processInstanceRepository.findAll(filterWithIncludeProcessNumbers)
```

The database query itself never inspects process variables — it only restricts on
the `includeProcessNumbers` set computed from the engine. See
[ProcessInstanceService.java:45](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/service/ProcessInstanceService.java)
and [ProcessInstanceRepositoryImpl.java:50](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/ProcessInstanceRepositoryImpl.java).

---

## 9. User Visibility

Variable filtering composes with a **user-visibility** specification on top of the
base query:

- If the caller is a **super admin**, no visibility constraints are added.
- Otherwise, when `filterByCurrentUser=true`, results are restricted to tasks
  where the current user is either the **assignee** (`assignedBy`) **or** is a
  member of one of the task's **candidate groups**. This is enforced by
  `userVisibilitySpec` at
  [TaskInstanceRepositoryImpl.java:429](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java).
- Client-supplied `candidateGroups` produces an additional `LIKE "%<group>%"`
  clause (stacked with the visibility spec).

The same visibility rule is applied in `getTaskStatisticsByUser` at
[TaskInstanceRepositoryImpl.java:382](../src/main/java/cv/igrp/platform/process/management/processruntime/infrastructure/persistence/repository/TaskInstanceRepositoryImpl.java),
so variable counts per user are consistent with the list endpoint.

Archived rows (`isArchived=true`) are always excluded.

---

## 10. Edge Cases & Gotchas

1. **Null values are not representable.** `VariablesExpression` rejects `null`
   values (`Objects.requireNonNull(value)`). There is no `IS NULL` / `IS NOT NULL`
   operator.

2. **Missing JSON paths silently return no rows.** `jsonb_extract_path_text`
   yields SQL `NULL` when a path segment doesn't exist; most operators then
   evaluate to `UNKNOWN` and the row is excluded. Use test data that has the key
   before asserting a bug.

3. **Type is inferred from JSON, not from the variable's original BPMN type.**
   `42` (integer JSON) is treated as `Number`; `"42"` (string JSON) is treated as
   `String`. A caller searching `priority = "7"` against a numeric variable will
   get a string-vs-string comparison against the JSONB text — which still works
   for `EQUALS`, but breaks `GREATER_THAN` etc.

4. **Numbers go through `to_number('999999999.999999')`.** Values with more than
   6 fractional digits, or in scientific notation not matching the mask, will
   fail to parse at the database level.

5. **Dates must be `YYYY-MM-DD`.** `to_date` uses a fixed mask. Times / offsets
   are not supported; full ISO timestamps are not parsed as dates.

6. **Booleans compare the JSONB text (`'true'` / `'false'`).** Sending `"value": 1`
   or `"value": "yes"` will not match a boolean variable.

7. **No `OR`, no `IN`, no `BETWEEN`.** All variable expressions in a request are
   ANDed. Ranges must be expressed as two expressions (`GTE` + `LTE`).

8. **No index on the JSONB column is created by the schema.** On large
   `t_task_instance` tables, variable filters perform full table scans with a
   functional expression. For production workloads consider adding either a GIN
   index (`USING gin (variables)`) or expression indexes on frequent
   `jsonb_extract_path_text(variables, 'name')` lookups.

9. **`LIKE` always wraps the value with `%…%`** — the caller cannot control the
   anchoring; prefix/suffix matches are not selectable.

10. **Case-insensitive string operators call `LOWER(jsonExpr)`**, so any
    functional index on the column must match the exact expression to be used.

11. **Pagination defaults** are `page=0`, `size=50`
    ([TaskInstanceFilter.java](../src/main/java/cv/igrp/platform/process/management/processruntime/domain/models/TaskInstanceFilter.java)).

12. **Process-instance variable filtering is eagerly materialized.** The engine
    is asked for *all* process numbers matching the variable expression, then the
    DB query filters with `includeProcessNumbers IN (…)`. Very broad queries can
    produce large `IN` lists.

---

## 11. Extension Points

To add a new operator:

1. Add a new enum constant to `VaribalesOperator`.
2. Add `case <OP> -> …` branches inside the relevant type block(s) in
   `TaskInstanceRepositoryImpl.buildOperatorPredicate`.
3. If the operator should apply to processes too, extend
   `RuntimeProcessEngineRepositoryImpl` so the engine mapping handles it.

To add a new value type (for example `LocalDateTime`):

1. Add a new `if (value instanceof …)` block in `buildOperatorPredicate`
   producing the right SQL cast.
2. Make sure the DTO → domain mapping in `TaskInstanceMapper.toFilter` coerces
   the incoming JSON into the new type.

---

## 12. Quick Reference

| Thing                    | Where                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------|
| Search endpoint          | `POST /tasks-instances/search`                                                                 |
| Body DTO                 | `VariablesFilterDTO { variables: VariablesExpressionDTO[] }`                                   |
| Expression DTO           | `VariablesExpressionDTO { name, operator, value }`                                             |
| Operator enum            | `VaribalesOperator` (note the iGRP-generated spelling)                                         |
| JSONB column             | `t_task_instance.variables`                                                                    |
| SQL extractor            | `jsonb_extract_path_text(variables, key1, key2, …)`                                            |
| Composition              | All expressions ANDed; no OR / NOT / IN / BETWEEN                                              |
| Visibility composition   | `filterByCurrentUser=true` + non-admin ⇒ assignee OR candidate-group match                     |
| Process variant          | `POST /process-instances/search`; engine returns matching process numbers; DB restricts by set |
