# iGRP Process – Delegate Configuration and Usages

## 1. Overview

In iGRP Process configuration, Service Tasks play a central role in automating actions within a workflow. These tasks can be enhanced using delegates, which are expressions that define how specific operations—such as webhook calls, JSON parsing, email sending, or message dispatching to brokers like Kafka—are executed.
Each delegate expression corresponds to a specific implementation logic. For example:
*	Webhook Delegate (`${igrpWebhookDelegate`}): Used to send HTTP requests to external services.
*	JSON Parser Delegate (`${igrpJsonParseDelegate}`): Parses raw or Base64-encoded JSON data.
*	Email Delegate (`${igrpSendEmailDelegate}`): Sends emails based on configured parameters.
*	Message Broker Delegate (`${igrpMessageBrokerSenderDelegate}`): Sends messages to Kafka or other message brokers.
*	External User Assignment Delegate (`${igrpExternalUserAssignmentDelegate}`): Calls an external API, extracts a user identifier from the response using a JSONPath expression, and creates a task assignment rule to automatically assign a target user task to that user.

These delegates allow dynamic and flexible integration with external systems, enabling data exchange, notifications, and event-driven communication within the process flow. Proper configuration of parameters and variable mapping ensures that data is correctly handled and passed between tasks.

## 2. Walkthrough

### 2.1 Scenario and Cases

This section outlines practical scenarios where delegates are used to perform specific actions:
•	Webhook Call Scenario: A service task sends a request to a "Users" endpoint. The response is stored in a variable (e.g., `testeData`) and specific fields are extracted using dot-notation or array indexing. These fields are then mapped into a payload for a subsequent webhook call.
•	JSON Parsing Scenario: A JSON string (either raw or Base64-encoded) is parsed using the JSON parser delegate. The result is stored in a variable (e.g., `parsedData`) and can be accessed directly or used in expressions for further processing.
These scenarios demonstrate how data flows between tasks and how delegates facilitate external communication and data transformation.

### 2.2 Steps

Here’s a clear breakdown of the steps involved in configuring and using delegates in service tasks:

1. Design your process

![BPMN Process Design](../assets/images/delegates/Imagem1.png)

2. In a ‘Service Task’ type, you must define an ID for it, because it is very important to fetch execution result data in a variable that is set in the process instance. To use a webhook call, the delegate expression is `${igrpWebhookDelegate}`.

![Webhook Delegate Service Task](../assets/images/delegates/Imagem2.png)

3. The available parameters for this delegate can be found in the table in attachment page. The required ones are `webhookUrl` to indicate the endpoint that should be called, and the `webhookMethod` to indicate the method of the request

![Webhook Delegate Service Task Parameters](../assets/images/delegates/Imagem3.png)

4. To fetch data from the previous service task, the response is saved in a variable called `<task-id>Data` (`testeData` in this scenario). The data in this variable can be accessed using dot-notation for objects, and through index elements for arrays, as presented in the configuration below

![Post Data Webhook Service Task](../assets/images/delegates/Imagem4.png)

This is the data fetched from Users webhook array that we want to map, and the goal is to attempt to fetch some fields in the response and send it to the Post Data Webhook:

![Payload for Post Data Webhook](../assets/images/delegates/Imagem5.png)

For that to happen, in the `webhookPayload`, using type expression, the use of notations allow to access the data value within the response

![Data Mapping in Post Data Webhook Payload](../assets/images/delegates/Imagem6.png)

After this step execution, below there’s the result for the webhook call, and it can be verified that the data were correctly mapped into the payload.

![Webhook Call Result](../assets/images/delegates/Imagem7.png)

To use the JSON parser delegate, the delegate expression is `${igrpJsonParseDelegate}`. It supports both raw JSON, and base 64 encoded JSON, controller by a boolean parameter (`isBase64Encoded`).

![JSON Parser Delegate Service Diagram](../assets/images/delegates/Imagem8.png)

![JSON Parser Delegate Service Task Configuration](../assets/images/delegates/Imagem9.png)

In this scenario, it will be encoded a JSON body

![JSON Encoded Body](../assets/images/delegates/Imagem10.png)

Then in the parameter `json`, the value can be set as a String, with no need for String normalization (like usages of \"id\" ...) or it can be indicated the encoded base 64 string. When it is a encoded JSON, the parameter `isBase64Encoded` must be set as `true`.

![JSON Parser Delegate Service Task Parameters](../assets/images/delegates/Imagem11.png)

Then the result is set in the variable `<task-id>Data` (`parsedData`), and then you can use it has an expression, pass the whole data, or access specific fields as shown in the webhook case.

![Post Parsed Data Service Task Configuration](../assets/images/delegates/Imagem13.png)

The result is shown below:

![Post Parsed Data Delegate Service Task Result](../assets/images/delegates/Imagem14.png)

5. To send emails, the delegate expression is `${igrpSendEmailDelegate}`.

![Email Delegate Service Task Diagram](../assets/images/delegates/Imagem15.png)

![Email Delegate Service Task Configuration](../assets/images/delegates/Imagem16.png)

Then set the values to configure the email destination, subject, content and sender

![Email Delegate Service Task Parameters](../assets/images/delegates/Imagem17.png)

Then on the step execution the email is sent according to the step configuration

![Email Delegate Service Task Result](../assets/images/delegates/Imagem18.png)

6. To send process data to a message broker, the delegate expression is `${igrpMessageBrokerSenderDelegate}`.

![Message Broker Sender Delegate Service Task Diagram](../assets/images/delegates/Imagem19.png)

![Message Broker Sender Delegate Service Task Configuration](../assets/images/delegates/Imagem20.png)

Then indicate the topic in the message broker that the message should be sent to through the variable `topic`

![Message Broker Sender Delegate Service Task Parameters](../assets/images/delegates/Imagem21.png)

Then on the service task execution it will send the process data to the message broker, as indicated below:

![Message Broker Sender Delegate Service Task Result](../assets/images/delegates/Imagem22.png)

If you don’t have a message broker available, an alternative is to use `${igrpProcessWebhookDelegate}`. It does the same thing as the message broker delegate, but through webhook. You only need to provide the URL (required), path and headers (if they are present). On execution, it triggers an HTTP POST request with the process data as payload to the provided URL with the provided headers

![Process Webhook Delegate Service Task Configuration](../assets/images/delegates/Imagem23.png)

#### Webhook failures, retries and reprocessing

Both webhook delegates record the outcome in process variables: `<task-id>StatusCode` and, on failure, `<task-id>Error` (`HTTP 503`, or the exception message when there was no response). Gateways can branch on them. A later success removes `<task-id>Error`.

What happens on failure depends on the service task:

| Service task | Retryable failure (no response, 5xx, 408, 429) | Other 4xx |
|---|---|---|
| synchronous (default) | variables recorded, process continues | variables recorded, process continues |
| `activiti:async="true"` | job retried, then parked as a failed job | variables recorded, process continues |

A synchronous task never fails the user action that triggered it. To get retries, mark the task async and, optionally, give it its own retry cycle:

```xml
<serviceTask id="notifyIrn" activiti:async="true" activiti:delegateExpression="${igrpWebhookDelegate}">
  <extensionElements>
    <!-- optional: 5 attempts, 10 minutes apart -->
    <activiti:failedJobRetryTimeCycle>R5/PT10M</activiti:failedJobRetryTimeCycle>
    <activiti:field name="webhookUrl"><activiti:string>https://...</activiti:string></activiti:field>
    <activiti:field name="webhookMethod"><activiti:string>POST</activiti:string></activiti:field>
  </extensionElements>
</serviceTask>
```

An async task runs after the request that reached it has returned, so the request cannot pick up the user task that follows. `EngineJobSyncListener` does it instead: once any engine job commits (async task, timer, a reprocessed failed job), it copies the new user tasks into the app and closes the process if the engine ended it. `TaskReconciler` is the safety net: every `IGRP_TASK_RECONCILE_INTERVAL` (5m) one pod finds the processes where app and engine disagree for longer than `IGRP_TASK_RECONCILE_GRACE` (2m) and runs the same sync on them.

Without `failedJobRetryTimeCycle` the defaults apply: `IGRP_ASYNC_JOB_RETRIES` (3) attempts, `IGRP_ASYNC_JOB_RETRY_WAIT` (60s) apart. These are engine-wide, so they cover every async service task, not only webhooks.

When the attempts run out the job becomes a dead-letter job and the process waits on that task. The `/failed-jobs` endpoints show and reprocess it:

| Endpoint | Permission | What it does |
|---|---|---|
| `GET /failed-jobs?processInstanceId=&page=&size=` | `FAILED_JOBS:visualizar` | Page of failed jobs: app process id, number and name, `executionId` (one per branch of a process), `activityId`, error message, `attempts`, `firstFailedAt`, `lastFailedAt` |
| `GET /failed-jobs/{jobId}/stacktrace` | `FAILED_JOBS:diagnosticar` | Full stack trace of the last failure (text/plain) |
| `POST /failed-jobs/{jobId}/retry` body `{"retries": 3}` (optional) | `FAILED_JOBS:reprocessar` | Puts the job back on the queue; it runs on the next job acquisition |

Activiti keeps no failure time and gives a retried job a new id each attempt, so `JobFailureRecorder` keeps its own record per execution (`t_job_failure`, V15): it counts each failure, reprocessing included, and clears the row on success. Failures from before V15 have no record and come back with `attempts` and the dates as null.

The process's activity list (`GET /activities/progress`) also shows these activities. The engine's history leaves them out, since a failed attempt rolls back, so they are added at the end of the list with status `FAILED` (out of retries) or `RETRYING`, each with `jobId`, `errorMessage`, `attempts` and the failure times. With parallel branches each failure is its own row.

Extra IRN permissions per tier go in `IRN_FAILED_JOBS_ACCEPT_READ`, `_DIAGNOSE` and `_RETRY`.

7. To dynamically assign a user task based on an external API response, the delegate expression is `${igrpExternalUserAssignmentDelegate}`.

This delegate is designed for scenarios where the assignee of a user task is determined by an external system. For example, a backoffice API may return which user should handle a specific request. The delegate calls that API, extracts the user identifier (e.g., email) from the JSON response using a JSONPath expression, and creates a `TaskAssignmentRule` so the target user task is automatically assigned to that user when it is activated.

### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `apiUrl` | Expression | Yes | — | The full URL of the external API endpoint. Supports process variable expressions (`${variableName}`) and environment variable placeholders (`$[ENV_VAR]`). Example: `$[BACKEND_URL]/api/v1/requests/by-service-id/${serviceId}` |
| `apiMethod` | Expression | No | `GET` | The HTTP method to use. Supported values: `GET`, `POST`, `PUT`, `DELETE`. |
| `apiPayload` | Expression | No | — | The request body for `POST` and `PUT` requests. Supports `${variableName}` and `$[ENV_VAR]` placeholders. |
| `jsonPathExpression` | Expression | Yes | — | A JSONPath expression used to extract the user identifier from the API response. Example: `$.data.assignedUserEmail` |
| `priorityJsonPathExpression` | Expression | No | — | An optional JSONPath expression used to extract the task priority from the same API response. The resolved value (a number, e.g. `3`) is stored on the assignment rule and applied to the target task when it is activated. Works exactly like `jsonPathExpression`. Example: `$.data.priority` |
| `targetTaskKey` | Expression | Yes | — | The task definition key (ID) of the user task that should be assigned to the resolved user. This must match the ID of a user task defined in the BPMN process. |
| `assignmentMode` | Expression | No | `ONE_TIME` | Determines how the assignment rule behaves. `ONE_TIME` means the rule is consumed after it is applied once. `ALWAYS` means the rule persists and is reapplied each time the target task is activated. |
| `outputVariable` | Expression | No | — | An optional process variable name where the extracted user identifier will be stored. Useful when you need to reference the resolved user in subsequent tasks or expressions. |

### Authentication

The delegate uses the global webhook authentication token configured via the application property `igrp.delegate.webhook.auth-token`. If this property is set, the delegate sends it as a `Bearer` token in the `Authorization` header of the HTTP request. No additional authentication configuration is needed on the delegate itself.

### How It Works

1. The delegate reads all configured parameters from the BPMN expression fields (with fallback to process variables of the same name).
2. All `$[ENV_VAR]` placeholders are resolved using system environment variables.
3. An HTTP request is made to the configured `apiUrl` using the specified `apiMethod`.
4. The JSON response body is parsed using the `jsonPathExpression` to extract the user identifier. If `priorityJsonPathExpression` is configured, the task priority is extracted from the same response in the same way.
5. The delegate checks if an active, unconsumed `TaskAssignmentRule` with an assignee already exists for the same `(processInstanceId, targetTaskKey)` combination:
   - **If found:** The existing rule's assignee (and priority, when resolved) is **updated** to the new values (no duplicate rule is created). This handles retries, loops, or re-entry scenarios safely.
   - **If not found:** A new `TaskAssignmentRule` is created and persisted with the extracted user as the direct assignee, and the resolved priority (if any).
6. When the target user task is activated later in the process, the assignment rule is automatically applied, assigning the task to the resolved user and setting its priority when the rule carries one.
7. Optionally, the extracted identifier is also stored in a process variable (if `outputVariable` is configured).

### Error Handling

- If the API call fails (HTTP error or connection failure), the error is logged and a transient variable `<taskId>Error` is set on the execution. The delegate returns without creating an assignment rule.
- If the JSONPath expression does not match any value in the response, or returns a null/blank value, the error is logged and a transient variable `<taskId>Error` is set. No assignment rule is created.
- If `priorityJsonPathExpression` is configured but does not match, returns a blank value, or does not resolve to an integer, a warning is logged and the rule is created/updated **without** a priority (the task keeps its default priority). Priority resolution never fails the task.
- Required parameters (`apiUrl`, `jsonPathExpression`, `targetTaskKey`) throw an `IllegalArgumentException` if not provided.

### Example Configuration

**Scenario:** A service task calls a backoffice API to determine which user should review a request. The API returns a JSON response containing the assigned user's email. The delegate extracts this email and assigns the next user task (`reviewRequest`) to that user.

**Service Task Configuration:**
- Delegate Expression: `${igrpExternalUserAssignmentDelegate}`

**Parameters:**

| Parameter | Value |
|-----------|-------|
| `apiUrl` | `$[BACKOFFICE_API_URL]/api/v1/requests/by-service-id/${serviceId}` |
| `apiMethod` | `GET` |
| `jsonPathExpression` | `$.data.assignedUserEmail` |
| `priorityJsonPathExpression` | `$.data.priority` |
| `targetTaskKey` | `reviewRequest` |
| `assignmentMode` | `ONE_TIME` |
| `outputVariable` | `assignedReviewerEmail` |

**Example API Response:**
```json
{
  "isSuccessfull": true,
  "Message": "Request found",
  "data": {
    "assignedUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "assignedUserName": "John Doe",
    "assignedUserEmail": "john.doe@example.com",
    "priority": 3
  }
}
```

With the JSONPath expression `$.data.assignedUserEmail`, the delegate extracts `john.doe@example.com` and creates an assignment rule so the `reviewRequest` user task is assigned to that user. The email is also stored in the process variable `assignedReviewerEmail` for use in subsequent tasks (e.g., sending a notification email). With `priorityJsonPathExpression` set to `$.data.priority`, the delegate also reads `3` from the response and applies it as the task priority when `reviewRequest` is activated.

### JSONPath Expression Examples

The JSONPath expression allows you to extract values from any JSON response structure:

| Response Structure | JSONPath Expression | Extracted Value |
|---|---|---|
| `{"data": {"email": "user@example.com"}}` | `$.data.email` | `user@example.com` |
| `{"result": {"user": {"contact": "user@example.com"}}}` | `$.result.user.contact` | `user@example.com` |
| `{"users": [{"email": "first@example.com"}]}` | `$.users[0].email` | `first@example.com` |
| `{"assignee": "user@example.com"}` | `$.assignee` | `user@example.com` |

### Environment Variable Placeholders

The `$[ENV_VAR]` syntax allows you to reference system environment variables in the parameter values. This is useful for configuring environment-specific URLs without hardcoding them in the BPMN process definition.

| Placeholder | Example Env Value | Usage |
|---|---|---|
| `$[BACKEND_URL]` | `https://api.example.com` | `$[BACKEND_URL]/api/v1/users` |
| `$[API_BASE_PATH]` | `/prc-cvt-requests-management` | `$[BACKEND_URL]$[API_BASE_PATH]/api/v1/requests` |

These placeholders are resolved before the HTTP request is made. If an environment variable is not found, the delegate throws a `RuntimeException` with a clear error message indicating which variable is missing.
