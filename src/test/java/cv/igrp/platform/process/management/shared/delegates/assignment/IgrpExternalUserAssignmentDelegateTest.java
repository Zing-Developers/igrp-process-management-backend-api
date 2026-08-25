package cv.igrp.platform.process.management.shared.delegates.assignment;

import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskAssignmentRuleRepository;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import cv.igrp.platform.process.management.shared.delegates.outbound.OutboundGuardProperties;
import cv.igrp.platform.process.management.shared.delegates.outbound.OutboundRequestGuard;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IgrpExternalUserAssignmentDelegateTest {

  @Mock private RestClient restClient;
  @Mock private TaskAssignmentRuleRepository taskAssignmentRuleRepository;
  @Mock private ProcessInstanceRepository processInstanceRepository;
  @Mock private DelegateExecution execution;
  @Mock private ProcessEngineConfiguration processEngineConfiguration;
  @Mock private RuntimeService runtimeService;

  @Mock private RestClient.RequestHeadersUriSpec<?> getSpec;
  @Mock private RestClient.RequestHeadersSpec<?> headersSpec;
  @Mock private RestClient.ResponseSpec responseSpec;
  @Mock private RestClient.RequestBodyUriSpec bodyUriSpec;
  @Mock(answer = org.mockito.Answers.RETURNS_SELF) private RestClient.RequestBodySpec bodySpec;

  private IgrpExternalUserAssignmentDelegate delegate;

  private static final String APP_PROCESS_INSTANCE_ID = "c58c94a5-c6f5-4ac2-829e-6ce902528d4a";
  private static final String ENGINE_PROCESS_INSTANCE_ID = "bae8ff66-60f2-11f1-aede-96097e7fe1f2";
  private static final String BUSINESS_KEY = "b0dfe9be-abbc-430c-9ae2-f179bb76f6a2";

  private static final String RESPONSE_JSON = """
      {
        "isSuccessfull": true,
        "data": {
          "assignedUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "assignedUserName": "John Doe",
          "assignedUserEmail": "john.doe@example.com",
          "priority": 3
        }
      }
      """;

  @BeforeEach
  void setUp() {
    delegate = new IgrpExternalUserAssignmentDelegate(restClient, taskAssignmentRuleRepository, processInstanceRepository,
        new OutboundRequestGuard(new OutboundGuardProperties(false, java.util.List.of("api.example.com"), java.util.List.of(), java.util.List.of("IGRP_WEBHOOK_*"), 1048576)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void successfulGetCallCreatesAssignmentRule() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    ArgumentCaptor<TaskAssignmentRule> captor = ArgumentCaptor.forClass(TaskAssignmentRule.class);
    verify(taskAssignmentRuleRepository).save(captor.capture());

    TaskAssignmentRule rule = captor.getValue();
    assertEquals("john.doe@example.com", rule.getAssignee().getValue());
    assertEquals("reviewTask", rule.getTaskDefinitionKey().getValue());
    assertEquals(TaskAssignmentMode.ONE_TIME, rule.getAssignmentMode());
    assertTrue(rule.isActive());
    assertFalse(rule.isConsumed());
  }

  @Test
  @SuppressWarnings("unchecked")
  void postMethodWithPayload() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/query");
    setupExpressionField("apiMethod", "POST");
    setupExpressionField("apiPayload", "{\"serviceId\": \"abc\"}");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupPostRequest(RESPONSE_JSON);

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository).save(any(TaskAssignmentRule.class));
  }

  @Test
  void missingApiUrlThrows() {
    setupExecution();
    setupExpressionField("jsonPathExpression", "$.data.email");
    setupExpressionField("targetTaskKey", "task1");

    assertThrows(IllegalArgumentException.class, () -> delegate.execute(execution));
  }

  @Test
  void missingJsonPathThrows() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/test");
    setupExpressionField("targetTaskKey", "task1");

    assertThrows(IllegalArgumentException.class, () -> delegate.execute(execution));
  }

  @Test
  void missingTargetTaskKeyThrows() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/test");
    setupExpressionField("jsonPathExpression", "$.data.email");

    assertThrows(IllegalArgumentException.class, () -> delegate.execute(execution));
  }

  @Test
  @SuppressWarnings("unchecked")
  void invalidJsonPathSetsError() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.nonexistent.path");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository, never()).save(any());
    verify(execution).setTransientVariable(eq("serviceTask1Error"), argThat(v -> v.toString().contains("JSONPath not found")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void apiErrorSetsTransientVariable() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/fail");
    setupExpressionField("jsonPathExpression", "$.data.email");
    setupExpressionField("targetTaskKey", "reviewTask");

    doReturn(getSpec).when(restClient).get();
    doReturn(headersSpec).when(getSpec).uri(anyString());
    doReturn(headersSpec).when(headersSpec).headers(any(Consumer.class));
    doThrow(new RestClientResponseException("Not Found", HttpStatusCode.valueOf(404), "Not Found", null, null, null))
        .when(headersSpec).retrieve();

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository, never()).save(any());
    verify(execution).setTransientVariable(eq("serviceTask1Error"), argThat(v -> v.toString().contains("API error 404")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void assignmentModeAlways() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupExpressionField("assignmentMode", "ALWAYS");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    ArgumentCaptor<TaskAssignmentRule> captor = ArgumentCaptor.forClass(TaskAssignmentRule.class);
    verify(taskAssignmentRuleRepository).save(captor.capture());
    assertEquals(TaskAssignmentMode.ALWAYS, captor.getValue().getAssignmentMode());
  }

  @Test
  @SuppressWarnings("unchecked")
  void outputVariableIsSet() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupExpressionField("outputVariable", "resolvedEmail");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    verify(runtimeService).setVariable(ENGINE_PROCESS_INSTANCE_ID, "resolvedEmail", "john.doe@example.com");
  }

  @Test
  @SuppressWarnings("unchecked")
  void defaultsToGetMethod() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    verify(restClient).get();
    verify(restClient, never()).post();
  }

  @Test
  @SuppressWarnings("unchecked")
  void processDefinitionKeyExtractedFromId() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    ArgumentCaptor<TaskAssignmentRule> captor = ArgumentCaptor.forClass(TaskAssignmentRule.class);
    verify(taskAssignmentRuleRepository).save(captor.capture());
    assertEquals("myProcess", captor.getValue().getProcessDefinitionKey().getValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  void existingRuleIsUpdatedInsteadOfCreatingNew() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    Identifier existingRuleId = Identifier.generate();
    TaskAssignmentRule existingRule = TaskAssignmentRule.builder()
        .id(existingRuleId)
        .processDefinitionKey(Code.create("myProcess"))
        .processInstanceId(Identifier.create(APP_PROCESS_INSTANCE_ID))
        .taskDefinitionKey(Code.create("reviewTask"))
        .assignee(Code.create("old.user@example.com"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .consumed(false)
        .active(true)
        .build();

    when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(any(), any()))
        .thenReturn(List.of(existingRule));

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository).updateAssignment(
        eq(existingRuleId),
        argThat(code -> "john.doe@example.com".equals(code.getValue())),
        eq(Set.of()),
        eq(Set.of()),
        isNull()
    );
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void existingRuleWithoutAssigneeCreatesNewRule() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    TaskAssignmentRule candidateOnlyRule = TaskAssignmentRule.builder()
        .processDefinitionKey(Code.create("myProcess"))
        .processInstanceId(Identifier.create(APP_PROCESS_INSTANCE_ID))
        .taskDefinitionKey(Code.create("reviewTask"))
        .candidateUsers(Set.of("candidate1@example.com"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .consumed(false)
        .active(true)
        .build();

    when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(any(), any()))
        .thenReturn(List.of(candidateOnlyRule));

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository, never()).updateAssignment(any(), any(), any(), any(), any());
    verify(taskAssignmentRuleRepository).save(any(TaskAssignmentRule.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void priorityIsExtractedFromResponseAndSetOnNewRule() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("priorityJsonPathExpression", "$.data.priority");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    ArgumentCaptor<TaskAssignmentRule> captor = ArgumentCaptor.forClass(TaskAssignmentRule.class);
    verify(taskAssignmentRuleRepository).save(captor.capture());
    assertEquals(3, captor.getValue().getPriority());
  }

  @Test
  @SuppressWarnings("unchecked")
  void priorityIsNullWhenPriorityJsonPathNotConfigured() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    delegate.execute(execution);

    ArgumentCaptor<TaskAssignmentRule> captor = ArgumentCaptor.forClass(TaskAssignmentRule.class);
    verify(taskAssignmentRuleRepository).save(captor.capture());
    assertNull(captor.getValue().getPriority());
  }

  @Test
  @SuppressWarnings("unchecked")
  void priorityIsPassedWhenUpdatingExistingRule() {
    setupExecution();
    setupExpressionField("apiUrl", "https://api.example.com/requests/123");
    setupExpressionField("jsonPathExpression", "$.data.assignedUserEmail");
    setupExpressionField("priorityJsonPathExpression", "$.data.priority");
    setupExpressionField("targetTaskKey", "reviewTask");
    setupGetRequest(RESPONSE_JSON);

    Identifier existingRuleId = Identifier.generate();
    TaskAssignmentRule existingRule = TaskAssignmentRule.builder()
        .id(existingRuleId)
        .processDefinitionKey(Code.create("myProcess"))
        .processInstanceId(Identifier.create(APP_PROCESS_INSTANCE_ID))
        .taskDefinitionKey(Code.create("reviewTask"))
        .assignee(Code.create("old.user@example.com"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .consumed(false)
        .active(true)
        .build();

    when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(any(), any()))
        .thenReturn(List.of(existingRule));

    delegate.execute(execution);

    verify(taskAssignmentRuleRepository).updateAssignment(
        eq(existingRuleId),
        argThat(code -> "john.doe@example.com".equals(code.getValue())),
        eq(Set.of()),
        eq(Set.of()),
        eq(3)
    );
  }

  private void setupExecution() {
    when(execution.getCurrentActivityId()).thenReturn("serviceTask1");
    when(execution.getProcessInstanceId()).thenReturn(ENGINE_PROCESS_INSTANCE_ID);
    when(execution.getProcessDefinitionId()).thenReturn("myProcess:1:deploy-1");
    when(execution.getProcessInstanceBusinessKey()).thenReturn(BUSINESS_KEY);
    lenient().when(execution.getVariable(anyString())).thenReturn(null);
    lenient().when(execution.getEngineServices()).thenReturn(processEngineConfiguration);
    lenient().when(processEngineConfiguration.getRuntimeService()).thenReturn(runtimeService);

    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.create(APP_PROCESS_INSTANCE_ID))
        .procReleaseKey(Code.create("myProcess"))
        .businessKey(Code.create(BUSINESS_KEY))
        .build();
    lenient().when(processInstanceRepository.findByBusinessKey(BUSINESS_KEY))
        .thenReturn(Optional.of(processInstance));

    lenient().when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(any(), any()))
        .thenReturn(List.of());
  }

  private void setupExpressionField(String fieldName, String value) {
    Expression expr = mock(Expression.class);
    lenient().when(expr.getValue(execution)).thenReturn(value);
    lenient().when(execution.getVariable(fieldName)).thenReturn(null);

    switch (fieldName) {
      case "apiUrl" -> delegate.apiUrl = expr;
      case "apiMethod" -> delegate.apiMethod = expr;
      case "apiPayload" -> delegate.apiPayload = expr;
      case "jsonPathExpression" -> delegate.jsonPathExpression = expr;
      case "priorityJsonPathExpression" -> delegate.priorityJsonPathExpression = expr;
      case "targetTaskKey" -> delegate.targetTaskKey = expr;
      case "assignmentMode" -> delegate.assignmentMode = expr;
      case "outputVariable" -> delegate.outputVariable = expr;
    }
  }

  @SuppressWarnings("unchecked")
  private void setupGetRequest(String responseBody) {
    doReturn(getSpec).when(restClient).get();
    doReturn(headersSpec).when(getSpec).uri(anyString());
    doReturn(headersSpec).when(headersSpec).headers(any(Consumer.class));
    doReturn(responseSpec).when(headersSpec).retrieve();
    doReturn(ResponseEntity.ok(responseBody)).when(responseSpec).toEntity(String.class);
  }

  @SuppressWarnings("unchecked")
  private void setupPostRequest(String responseBody) {
    doReturn(bodyUriSpec).when(restClient).post();
    doReturn(bodySpec).when(bodyUriSpec).uri(anyString());
    doReturn(responseSpec).when(bodySpec).retrieve();
    doReturn(ResponseEntity.ok(responseBody)).when(responseSpec).toEntity(String.class);
  }
}
