package cv.igrp.platform.process.management.shared.delegates.assignment;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskAssignmentRuleRepository;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;

@Component("igrpExternalUserAssignmentDelegate")
public class IgrpExternalUserAssignmentDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(IgrpExternalUserAssignmentDelegate.class);

  private final RestClient restClient;
  private final TaskAssignmentRuleRepository taskAssignmentRuleRepository;
  private final ProcessInstanceRepository processInstanceRepository;

  public IgrpExternalUserAssignmentDelegate(
      RestClient restClient,
      TaskAssignmentRuleRepository taskAssignmentRuleRepository,
      ProcessInstanceRepository processInstanceRepository
  ) {
    this.restClient = restClient;
    this.taskAssignmentRuleRepository = taskAssignmentRuleRepository;
    this.processInstanceRepository = processInstanceRepository;
  }

  @Value(value = "${igrp.delegate.webhook.auth-token:}")
  private String globalAuthToken;

  public Expression apiUrl;
  public Expression apiMethod;
  public Expression apiPayload;
  public Expression jsonPathExpression;
  public Expression targetTaskKey;
  public Expression assignmentMode;
  public Expression outputVariable;

  @Override
  public void execute(DelegateExecution execution) {
    String taskId = execution.getCurrentActivityId();
    String engineProcessInstanceId = execution.getProcessInstanceId();
    String processDefinitionId = execution.getProcessDefinitionId();
    String businessKey = execution.getProcessInstanceBusinessKey();

    log.info("[ExternalUserAssignment] Executing task: {} from process instance: {} (businessKey: {})",
        taskId, engineProcessInstanceId, businessKey);

    ProcessInstance processInstance = processInstanceRepository.findByBusinessKey(businessKey)
        .orElseThrow(() -> new IllegalStateException(
            "No process instance found for businessKey: " + businessKey));
    Identifier processInstanceId = processInstance.getId();

    log.info("[ExternalUserAssignment] Resolved application processInstanceId: {} from businessKey: {}",
        processInstanceId.getValue(), businessKey);

    String url = resolveField(execution, "apiUrl", apiUrl);
    url = EnvVarUtil.resolveEnvVars(url, "apiUrl");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("apiUrl is required and was not provided");
    }

    String method = ofNullable(resolveField(execution, "apiMethod", apiMethod)).orElse("GET");
    method = EnvVarUtil.resolveEnvVars(method, "apiMethod").toUpperCase();

    String payload = resolveField(execution, "apiPayload", apiPayload);
    if (payload != null) {
      payload = EnvVarUtil.resolveEnvVars(payload, "apiPayload");
    }

    String jsonPath = resolveField(execution, "jsonPathExpression", jsonPathExpression);
    jsonPath = EnvVarUtil.resolveEnvVars(jsonPath, "jsonPathExpression");
    if (jsonPath == null || jsonPath.isBlank()) {
      throw new IllegalArgumentException("jsonPathExpression is required and was not provided");
    }

    String targetTask = resolveField(execution, "targetTaskKey", targetTaskKey);
    targetTask = EnvVarUtil.resolveEnvVars(targetTask, "targetTaskKey");
    if (targetTask == null || targetTask.isBlank()) {
      throw new IllegalArgumentException("targetTaskKey is required and was not provided");
    }

    String modeStr = ofNullable(resolveField(execution, "assignmentMode", assignmentMode)).orElse("ONE_TIME");
    modeStr = EnvVarUtil.resolveEnvVars(modeStr, "assignmentMode");
    TaskAssignmentMode mode = TaskAssignmentMode.fromValue(modeStr);
    if (mode == null) {
      mode = TaskAssignmentMode.ONE_TIME;
    }

    String outputVar = resolveField(execution, "outputVariable", outputVariable);
    if (outputVar != null) {
      outputVar = EnvVarUtil.resolveEnvVars(outputVar, "outputVariable");
    }

    String responseBody = callApi(execution, taskId, url, method, payload);
    if (responseBody == null) {
      return;
    }

    String userIdentifier = extractUserIdentifier(execution, taskId, responseBody, jsonPath);
    if (userIdentifier == null) {
      return;
    }

    log.info("[ExternalUserAssignment] Resolved user identifier: {} for target task: {}", userIdentifier, targetTask);

    String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);

    List<TaskAssignmentRule> existingRules = taskAssignmentRuleRepository
        .findActiveByProcessInstanceAndTaskDefinition(
            processInstanceId,
            Code.create(targetTask)
        );

    Optional<TaskAssignmentRule> updatableRule = existingRules.stream()
        .filter(TaskAssignmentRule::hasAssignee)
        .findFirst();

    if (updatableRule.isPresent()) {
      taskAssignmentRuleRepository.updateAssignment(
          updatableRule.get().getId(),
          Code.create(userIdentifier),
          Set.of()
      );
      log.info("[ExternalUserAssignment] Updated existing rule: id={}, assignee={}",
          updatableRule.get().getId().getValue(), userIdentifier);
    } else {
      taskAssignmentRuleRepository.save(TaskAssignmentRule.builder()
          .processDefinitionKey(Code.create(processDefinitionKey))
          .processInstanceId(processInstanceId)
          .taskDefinitionKey(Code.create(targetTask))
          .assignee(Code.create(userIdentifier))
          .assignmentMode(mode)
          .consumed(false)
          .active(true)
          .build()
      );
      log.info("[ExternalUserAssignment] Created new rule: processInstance={}, targetTask={}, assignee={}, mode={}",
          processInstanceId.getValue(), targetTask, userIdentifier, mode);
    }

    if (outputVar != null && !outputVar.isBlank()) {
      execution.getEngineServices().getRuntimeService().setVariable(
          engineProcessInstanceId, outputVar, userIdentifier);
    }
  }

  private String callApi(DelegateExecution execution, String taskId, String url, String method, String payload) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (globalAuthToken != null && !globalAuthToken.isEmpty()) {
        headers.set("Authorization", "Bearer " + globalAuthToken);
      }

      log.info("[ExternalUserAssignment] Sending {} request to {}", method, url);

      var response = switch (method) {
        case "GET" -> restClient.get()
            .uri(url)
            .headers(h -> h.addAll(headers))
            .retrieve()
            .toEntity(String.class);
        case "POST" -> restClient.post()
            .uri(url)
            .headers(h -> h.addAll(headers))
            .body(payload != null ? payload : "")
            .retrieve()
            .toEntity(String.class);
        case "PUT" -> restClient.put()
            .uri(url)
            .headers(h -> h.addAll(headers))
            .body(payload != null ? payload : "")
            .retrieve()
            .toEntity(String.class);
        case "DELETE" -> restClient.delete()
            .uri(url)
            .headers(h -> h.addAll(headers))
            .retrieve()
            .toEntity(String.class);
        default -> throw new IllegalArgumentException("Unsupported apiMethod: " + method);
      };

      log.info("[ExternalUserAssignment] Response {}: {}", response.getStatusCode().value(), response.getBody());
      return response.getBody();

    } catch (RestClientResponseException e) {
      log.error("[ExternalUserAssignment] API returned error {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
      execution.setTransientVariable(taskId + "Error", "API error " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
      return null;
    } catch (Exception e) {
      log.error("[ExternalUserAssignment] Error calling API {}", url, e);
      execution.setTransientVariable(taskId + "Error", e.getMessage());
      return null;
    }
  }

  private String extractUserIdentifier(DelegateExecution execution, String taskId, String responseBody, String jsonPath) {
    try {
      Object result = JsonPath.read(responseBody, jsonPath);
      if (result == null) {
        log.error("[ExternalUserAssignment] JSONPath '{}' returned null", jsonPath);
        execution.setTransientVariable(taskId + "Error", "JSONPath expression returned null");
        return null;
      }
      String identifier = result.toString().trim();
      if (identifier.isBlank()) {
        log.error("[ExternalUserAssignment] JSONPath '{}' returned blank value", jsonPath);
        execution.setTransientVariable(taskId + "Error", "JSONPath expression returned blank value");
        return null;
      }
      return identifier;
    } catch (PathNotFoundException e) {
      log.error("[ExternalUserAssignment] JSONPath '{}' not found in response", jsonPath);
      execution.setTransientVariable(taskId + "Error", "JSONPath not found: " + jsonPath);
      return null;
    } catch (Exception e) {
      log.error("[ExternalUserAssignment] Error evaluating JSONPath '{}'", jsonPath, e);
      execution.setTransientVariable(taskId + "Error", "JSONPath error: " + e.getMessage());
      return null;
    }
  }

  // Activiti processDefinitionId format: "processKey:version:deploymentId"
  private String extractProcessDefinitionKey(String processDefinitionId) {
    if (processDefinitionId != null && processDefinitionId.contains(":")) {
      return processDefinitionId.substring(0, processDefinitionId.indexOf(':'));
    }
    return processDefinitionId;
  }

  private String resolveField(DelegateExecution execution, String variableName, Expression expression) {
    Object variable = execution.getVariable(variableName);
    Object value = Objects.nonNull(variable) ? variable : Objects.nonNull(expression) ? expression.getValue(execution) : null;
    return value != null ? value.toString() : null;
  }
}
