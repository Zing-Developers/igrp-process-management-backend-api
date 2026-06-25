package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.*;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.application.constants.VaribalesOperator;
import cv.igrp.platform.process.management.processruntime.domain.repository.*;
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.*;
import cv.igrp.platform.process.management.shared.security.util.UserContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskInstanceServiceTest {

  @Mock
  private TaskInstanceRepository taskInstanceRepository;

  @Mock
  private TaskInstanceEventRepository taskInstanceEventRepository;

  @Mock
  private TaskAssignmentRuleRepository taskAssignmentRuleRepository;

  @Mock
  private RuntimeProcessEngineRepository runtimeProcessEngineRepository;

  @Mock
  private ProcessInstanceRepository processInstanceRepository;

  @Mock
  private ProcessDefinitionRepository processDefinitionRepository;

  @Mock
  private UserProfileRepository userProfileRepository;

  @Mock
  private UserContext userContext;

  @Spy
  @InjectMocks
  private TaskInstanceService taskInstanceService;

  private Code currentUser;

  @BeforeEach
  void setup() {
    currentUser = Code.create("demo@nosi.cv");
  }

  /* ============================================================
   *  createTaskInstancesByProcess
   * ============================================================ */

  @Test
  void testCreateTaskInstancesByProcess() {

    Identifier processInstanceId = Identifier.create(UUID.randomUUID());
    Code engineProcessNumber = Code.create("ENG-PROC-123");
    String startedBy = "igrp@nosi.cv";
    String taskKey = "task-1";
    String formKey = "FORM-001";
    Code procReleaseId = Code.create("PROC-RELEASE-123");

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getEngineProcessNumber()).thenReturn(engineProcessNumber);
    when(processInstance.getStartedBy()).thenReturn(startedBy);
    when(processInstance.getProcReleaseId()).thenReturn(procReleaseId);

    TaskInstance task = TaskInstance.builder()
        .taskKey(Code.create(taskKey))
        .externalId(Code.create(UUID.randomUUID().toString()))
        .name(Name.create("Task-1"))
        .startedAt(LocalDateTime.now())
        .build();

    TaskInstance spyTask = Mockito.spy(task);

    when(runtimeProcessEngineRepository.getActiveTaskInstances(engineProcessNumber.getValue()))
        .thenReturn(List.of(spyTask));

    ProcessArtifact artifact = mock(ProcessArtifact.class);
    when(artifact.getKey()).thenReturn(Code.create(taskKey));
    when(artifact.getFormKey()).thenReturn(formKey);
    when(artifact.getCandidateGroups()).thenReturn(Set.of());

    when(processDefinitionRepository.findAllArtifacts(any()))
        .thenReturn(List.of(artifact));

    doReturn(spyTask).when(spyTask).withProperties(any(), any(), any());
    doNothing().when(spyTask).create();
    when(spyTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));

    taskInstanceService.createTaskInstancesByProcess(processInstance);

    verify(spyTask).withProperties(
        eq(processInstance),
        eq(Code.create(formKey)),
        eq(Code.create(startedBy))
    );

    verify(spyTask).create();
    verify(taskInstanceRepository).create(spyTask);
    verify(taskInstanceEventRepository).save(any(TaskInstanceEvent.class));
  }

  @Test
  void createTaskInstancesByProcess_shouldApplyStartAssigneeRuleBeforeCandidateAssignments() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.generate())
        .procReleaseKey(Code.create("PROC_KEY"))
        .procReleaseId(Code.create("PROC_RELEASE_ID"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("BUS-1"))
        .startedBy("demo@nosi.cv")
        .priority(10)
        .assignmentRules(List.of(TaskAssignmentRuleRequest.builder()
            .taskKey(Code.create("task-1"))
            .assignee(Code.create("owner@nosi.cv"))
            .assignmentMode(TaskAssignmentMode.ALWAYS)
            .priority(7)
            .build()))
        .build();

    TaskInstance runtimeTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .startedAt(LocalDateTime.now())
        .build();

    TaskInstance persistedTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .processInstanceId(processInstance.getId())
        .processKey(processInstance.getProcReleaseKey())
        .startedBy(Code.create("demo@nosi.cv"))
        .status(TaskInstanceStatus.CREATED)
        .taskInstanceEvents(new ArrayList<>())
        .build();

    ProcessArtifact artifact = mock(ProcessArtifact.class);
    when(artifact.getKey()).thenReturn(Code.create("task-1"));
    when(artifact.getFormKey()).thenReturn(null);
    when(artifact.getCandidateGroups()).thenReturn(Set.of("group1"));
    when(artifact.getDueDate()).thenReturn(null);

    when(runtimeProcessEngineRepository.getActiveTaskInstances("ENG-PROC-123"))
        .thenReturn(List.of(runtimeTask));
    when(processDefinitionRepository.findAllArtifacts(processInstance.getProcReleaseId()))
        .thenReturn(List.of(artifact));
    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(persistedTask));

    taskInstanceService.createTaskInstancesByProcess(processInstance);

    verify(runtimeProcessEngineRepository).assignTask(externalId, "owner@nosi.cv", null);
    verify(runtimeProcessEngineRepository, never()).addCandidateGroup(externalId, "group1");
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  @Test
  void createTaskInstancesByProcess_shouldConsumePersistedOneTimeCandidateUserRule() {

    UUID taskId = UUID.randomUUID();
    UUID ruleId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.generate())
        .procReleaseKey(Code.create("PROC_KEY"))
        .procReleaseId(Code.create("PROC_RELEASE_ID"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("BUS-1"))
        .startedBy("demo@nosi.cv")
        .priority(10)
        .build();

    TaskAssignmentRule persistedRule = TaskAssignmentRule.builder()
        .id(Identifier.create(ruleId))
        .processDefinitionKey(processInstance.getProcReleaseKey())
        .processInstanceId(processInstance.getId())
        .taskDefinitionKey(Code.create("task-1"))
        .candidateUsers(List.of("candidate@nosi.cv"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .priority(7)
        .persisted(true)
        .build();

    TaskInstance runtimeTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .startedAt(LocalDateTime.now())
        .build();

    TaskInstance persistedTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .processInstanceId(processInstance.getId())
        .processKey(processInstance.getProcReleaseKey())
        .startedBy(Code.create("demo@nosi.cv"))
        .status(TaskInstanceStatus.CREATED)
        .taskInstanceEvents(new ArrayList<>())
        .build();

    ProcessArtifact artifact = mock(ProcessArtifact.class);
    when(artifact.getKey()).thenReturn(Code.create("task-1"));
    when(artifact.getFormKey()).thenReturn(null);
    when(artifact.getCandidateGroups()).thenReturn(Set.of());
    when(artifact.getDueDate()).thenReturn(null);

    when(runtimeProcessEngineRepository.getActiveTaskInstances("ENG-PROC-123"))
        .thenReturn(List.of(runtimeTask));
    when(processDefinitionRepository.findAllArtifacts(processInstance.getProcReleaseId()))
        .thenReturn(List.of(artifact));
    when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(
        processInstance.getId(),
        Code.create("task-1")
    )).thenReturn(List.of(persistedRule));
    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(persistedTask));

    taskInstanceService.createTaskInstancesByProcess(processInstance);

    verify(runtimeProcessEngineRepository).addCandidateUser(externalId, "candidate@nosi.cv");
    verify(taskAssignmentRuleRepository).markConsumed(Identifier.create(ruleId), Identifier.create(taskId));
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  @Test
  void createTaskInstancesByProcess_shouldConsumePersistedOneTimeCandidateGroupRule() {

    UUID taskId = UUID.randomUUID();
    UUID ruleId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.generate())
        .procReleaseKey(Code.create("PROC_KEY"))
        .procReleaseId(Code.create("PROC_RELEASE_ID"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("BUS-1"))
        .startedBy("demo@nosi.cv")
        .priority(10)
        .build();

    TaskAssignmentRule persistedRule = TaskAssignmentRule.builder()
        .id(Identifier.create(ruleId))
        .processDefinitionKey(processInstance.getProcReleaseKey())
        .processInstanceId(processInstance.getId())
        .taskDefinitionKey(Code.create("task-1"))
        .candidateGroups(List.of("group-1"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .priority(7)
        .persisted(true)
        .build();

    TaskInstance runtimeTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .startedAt(LocalDateTime.now())
        .build();

    TaskInstance persistedTask = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("task-1"))
        .externalId(Code.create(externalId))
        .name(Name.create("Task-1"))
        .processInstanceId(processInstance.getId())
        .processKey(processInstance.getProcReleaseKey())
        .startedBy(Code.create("demo@nosi.cv"))
        .status(TaskInstanceStatus.CREATED)
        .taskInstanceEvents(new ArrayList<>())
        .build();

    ProcessArtifact artifact = mock(ProcessArtifact.class);
    when(artifact.getKey()).thenReturn(Code.create("task-1"));
    when(artifact.getFormKey()).thenReturn(null);
    when(artifact.getCandidateGroups()).thenReturn(Set.of());
    when(artifact.getDueDate()).thenReturn(null);

    when(runtimeProcessEngineRepository.getActiveTaskInstances("ENG-PROC-123"))
        .thenReturn(List.of(runtimeTask));
    when(processDefinitionRepository.findAllArtifacts(processInstance.getProcReleaseId()))
        .thenReturn(List.of(artifact));
    when(taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(
        processInstance.getId(),
        Code.create("task-1")
    )).thenReturn(List.of(persistedRule));
    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(persistedTask));

    taskInstanceService.createTaskInstancesByProcess(processInstance);

    verify(runtimeProcessEngineRepository).addCandidateGroup(externalId, "group-1");
    verify(taskAssignmentRuleRepository).markConsumed(Identifier.create(ruleId), Identifier.create(taskId));
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  /* ============================================================
   *  getByIdWithEvents
   * ============================================================ */

  private TaskInstance getMockTaskInstance(Identifier id, boolean withEvents) {
    return TaskInstance.builder()
        .id(id)
        .name(Name.create("Task1"))
        .taskKey(Code.create("task_key1"))
        .externalId(Code.create(UUID.randomUUID().toString()))
        .processNumber(ProcessNumber.create("PROC_123456"))
        .taskInstanceEvents(withEvents ? List.of(mock(TaskInstanceEvent.class)) : null)
        .build();
  }

  @Test
  void getByIdWihEvents_shouldReturnTaskInstance_whenFound() {

    UUID id = UUID.randomUUID();
    TaskInstance mockTask = getMockTaskInstance(Identifier.create(id), true);

    when(taskInstanceRepository.findByIdWithEvents(id))
        .thenReturn(Optional.of(mockTask));

    TaskInstance result = taskInstanceService.getByIdWihEvents(Identifier.create(id));

    assertNotNull(result);
    assertEquals(mockTask, result);
  }

  @Test
  void getByIdWihEvents_shouldThrowException_whenNotFound() {

    UUID id = UUID.randomUUID();

    when(taskInstanceRepository.findByIdWithEvents(id))
        .thenReturn(Optional.empty());

    IgrpResponseStatusException ex = assertThrows(
        IgrpResponseStatusException.class,
        () -> taskInstanceService.getByIdWihEvents(Identifier.create(id))
    );

    assertTrue(ex.getMessage().contains("No Task Instance found with id"));
    assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
  }

  /* ============================================================
   *  claim / assign / unclaim
   * ============================================================ */

  @Test
  void claimTask_shouldClaimTaskAndCallRepositories() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    TaskInstance mockTask = mock(TaskInstance.class);
    when(mockTask.getExternalId()).thenReturn(Code.create(externalId));
    when(mockTask.getAssignedBy()).thenReturn(currentUser);
    when(mockTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));

    TaskOperationData operation = TaskOperationData.builder()
        .id(taskId.toString())
        .currentUser(currentUser)
        .build();

    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(mockTask));

    taskInstanceService.claimTask(operation);

    verify(mockTask).claim(operation);
    verify(taskInstanceRepository).update(mockTask);
    verify(taskInstanceEventRepository).save(any(TaskInstanceEvent.class));
    verify(runtimeProcessEngineRepository)
        .claimTask(externalId, currentUser.getValue());
  }

  @Test
  void assignTask_shouldAssignTaskAndCallRepositories() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    TaskInstance mockTask = mock(TaskInstance.class);
    when(mockTask.getExternalId()).thenReturn(Code.create(externalId));
    when(mockTask.getAssignedBy()).thenReturn(Code.create("igrp@nosi.cv"));
    when(mockTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));
    TaskOperationData operation = TaskOperationData.builder()
        .id(taskId.toString())
        .currentUser(currentUser)
        .targetUser("igrp@nosi.cv")
        .note("Assigning task")
        .build();

    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(mockTask));

    taskInstanceService.assignTask(operation);

    verify(mockTask).assignUser(operation);
    verify(taskInstanceRepository).update(mockTask);
    verify(taskInstanceEventRepository).save(any(TaskInstanceEvent.class));
    verify(runtimeProcessEngineRepository)
        .assignTask(externalId, "igrp@nosi.cv", "Assigning task");
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  @Test
  void assignTask_shouldNotPersistAssignmentRule() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    TaskInstance mockTask = mock(TaskInstance.class);
    when(mockTask.getExternalId()).thenReturn(Code.create(externalId));
    when(mockTask.getAssignedBy()).thenReturn(Code.create("igrp@nosi.cv"));
    when(mockTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));

    TaskOperationData operation = TaskOperationData.builder()
        .id(taskId.toString())
        .currentUser(currentUser)
        .targetUser("igrp@nosi.cv")
        .note("Assigning task")
        .build();

    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(mockTask));

    taskInstanceService.assignTask(operation);

    verify(mockTask).assignUser(operation);
    verify(runtimeProcessEngineRepository)
        .assignTask(externalId, "igrp@nosi.cv", "Assigning task");
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
  }

  @Test
  void assignTask_shouldAddCandidateGroupsAndUsersAndCallRuntime() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    TaskInstance mockTask = mock(TaskInstance.class);
    when(mockTask.getExternalId()).thenReturn(Code.create(externalId));
    when(mockTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));
    TaskOperationData operation = TaskOperationData.builder()
        .id(taskId.toString())
        .currentUser(currentUser)
        .candidateGroups(List.of("group1", "group2"))
        .candidateUsers(List.of("candidate1@nosi.cv", "candidate2@nosi.cv"))
        .note("Assigning candidates")
        .build();

    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(mockTask));

    taskInstanceService.assignTask(operation);

    verify(mockTask).addCandidates(operation);
    verify(runtimeProcessEngineRepository).addCandidateGroup(externalId, "group1");
    verify(runtimeProcessEngineRepository).addCandidateGroup(externalId, "group2");
    verify(runtimeProcessEngineRepository).addCandidateUser(externalId, "candidate1@nosi.cv");
    verify(runtimeProcessEngineRepository).addCandidateUser(externalId, "candidate2@nosi.cv");
    verify(taskAssignmentRuleRepository, never()).save(any(TaskAssignmentRule.class));
    verify(taskInstanceRepository).update(mockTask);
    verify(taskInstanceEventRepository).save(any(TaskInstanceEvent.class));
  }

  @Test
  void unClaimTask_shouldUnClaimTaskAndCallRepositories() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    TaskInstance mockTask = mock(TaskInstance.class);
    when(mockTask.getExternalId()).thenReturn(Code.create(externalId));
    when(mockTask.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));

    TaskOperationData operation = TaskOperationData.builder()
        .id(taskId.toString())
        .currentUser(currentUser)
        .build();

    when(taskInstanceRepository.findByIdWithEvents(taskId))
        .thenReturn(Optional.of(mockTask));

    taskInstanceService.unClaimTask(operation);

    verify(mockTask).unClaim(operation);
    verify(taskInstanceRepository).update(mockTask);
    verify(taskInstanceEventRepository).save(any(TaskInstanceEvent.class));
    verify(runtimeProcessEngineRepository).unClaimTask(externalId);
  }

  /* ============================================================
   *  completeTask
   * ============================================================ */

  @Test
  void shouldCompleteTaskSuccessfully() {

    UUID taskId = UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();

    Map<String, Object> variables = Map.of("v1", "val1");

    TaskOperationData operation = mock(TaskOperationData.class);
    when(operation.getId()).thenReturn(Identifier.create(taskId));
    when(operation.getCurrentUser()).thenReturn(currentUser);
    when(operation.getVariables()).thenReturn(variables);
    doNothing().when(operation).validateVariablesAndForms();

    TaskInstance task = mock(TaskInstance.class);
    when(task.getExternalId()).thenReturn(Code.create(externalId));
    when(task.getProcessInstanceId()).thenReturn(Identifier.generate());
    when(task.getTaskInstanceEvents()).thenReturn(List.of(mock(TaskInstanceEvent.class)));

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getEngineProcessNumber()).thenReturn(Code.create("ENG-PROC-123"));

    ProcessInstance activityProcess = mock(ProcessInstance.class);
    when(activityProcess.getStatus()).thenReturn(ProcessInstanceStatus.COMPLETED);
    when(activityProcess.getEndedAt()).thenReturn(LocalDateTime.now());
    when(activityProcess.getEndedBy()).thenReturn(null);

    doReturn(task).when(taskInstanceService).getByIdWihEvents(Identifier.create(taskId));
    when(processInstanceRepository.findById(any())).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceById(any())).thenReturn(activityProcess);

    taskInstanceService.completeTask(operation);

    verify(task).complete(operation);
    verify(runtimeProcessEngineRepository)
        .completeTask(externalId, null, variables);
    verify(processInstanceRepository).save(processInstance);
  }

  /* ============================================================
   *  statistics
   * ============================================================ */

  @Test
  void testGetGlobalTaskStatistics() {

    TaskStatistics stats = TaskStatistics.builder()
        .totalTaskInstances(10L)
        .totalAvailableTasks(5L)
        .totalAssignedTasks(2L)
        .totalSuspendedTasks(1L)
        .totalCompletedTasks(1L)
        .totalCanceledTasks(1L)
        .build();

    when(taskInstanceRepository.getGlobalTaskStatistics()).thenReturn(stats);

    TaskStatistics result = taskInstanceService.getGlobalTaskStatistics();

    assertEquals(stats, result);
  }

  @Test
  void testGetTaskStatisticsByUser() {

    TaskStatistics stats = TaskStatistics.builder()
        .totalTaskInstances(10L)
        .build();
  /*
    when(taskInstanceRepository.getTaskStatisticsByUser(bindCurrentUser)).thenReturn(stats);

    TaskStatistics result = taskInstanceService.getTaskStatisticsByUser(bindCurrentUser);

    assertEquals(stats, result);

   */
  }

  /* ============================================================
   *  getAllTaskInstances — batch behavior
   * ============================================================ */

  @Test
  void getAllTaskInstances_shouldUseBatchMethodsForVariablesAndProfiles() {

    // Setup filter (no current-user filtering, no variable filtering)
    TaskInstanceFilter filter = mock(TaskInstanceFilter.class);
    when(filter.isFilterByCurrentUser()).thenReturn(false);
    when(filter.getVariablesExpressions()).thenReturn(List.of());

    // Setup task event
    TaskInstanceEvent event = mock(TaskInstanceEvent.class);
    when(event.getPerformedBy()).thenReturn(Code.create("performer@nosi.cv"));

    // Setup task instance
    TaskInstance task = mock(TaskInstance.class);
    when(task.getEngineProcessNumber()).thenReturn("ENG-001");
    when(task.getStartedBy()).thenReturn(Code.create("starter@nosi.cv"));
    when(task.getEndedBy()).thenReturn(null);
    when(task.getAssignedBy()).thenReturn(Code.create("assignee@nosi.cv"));
    when(task.getTaskInstanceEvents()).thenReturn(List.of(event));

    PageableLista<TaskInstance> page = new PageableLista<>(0, 10, 1L, 1, true, true, List.of(task));
    when(taskInstanceRepository.findAll(filter)).thenReturn(page);

    // Mock batch process variables
    when(runtimeProcessEngineRepository.getProcessVariablesBatch(any()))
        .thenReturn(Map.of("ENG-001", Map.of("var1", "val1")));

    // Mock batch user profiles
    when(userProfileRepository.findBySubjectOrEmails(any(), any()))
        .thenReturn(List.of());

    // Execute
    taskInstanceService.getAllTaskInstances(filter);

    // Verify batch process variables called once
    verify(runtimeProcessEngineRepository).getProcessVariablesBatch(any());

    // Verify batch user profiles called once
    verify(userProfileRepository).findBySubjectOrEmails(any(), any());

    // Verify individual methods are NEVER called in the list path
    verify(runtimeProcessEngineRepository, never()).getProcessVariables(anyString());
    verify(userProfileRepository, never()).findBySubjectOrEmail(anyString(), anyString());
  }

  @Test
  void getAllTaskInstances_shouldDelegateVariableFilteringToEngine_andCollectEngineProcessNumbers() {

    VariablesExpression expression = VariablesExpression.builder()
        .name("status")
        .operator(VaribalesOperator.EQUALS)
        .value("ACTIVE")
        .build();

    TaskInstanceFilter filter = TaskInstanceFilter.builder()
        .variablesExpressions(new ArrayList<>(List.of(expression)))
        .build();

    ProcessInstance engineMatch = mock(ProcessInstance.class);
    when(engineMatch.getEngineProcessNumber()).thenReturn(Code.create("ENG-001"));

    when(runtimeProcessEngineRepository.getAllProcessInstancesByVariables(filter.getVariablesExpressions()))
        .thenReturn(List.of(engineMatch));

    PageableLista<TaskInstance> page = new PageableLista<>(0, 50, 0L, 0, true, true, List.of());
    when(taskInstanceRepository.findAll(filter)).thenReturn(page);

    taskInstanceService.getAllTaskInstances(filter);

    // Engine was queried for process variables, and the matching engine process number was
    // collected onto the filter so the repository can restrict tasks by their parent process.
    verify(runtimeProcessEngineRepository).getAllProcessInstancesByVariables(filter.getVariablesExpressions());
    assertTrue(filter.getEngineProcessNumbers().contains("ENG-001"));
  }

  @Test
  void getAllTaskInstances_shouldLeaveEngineProcessNumbersEmpty_whenEngineReturnsNoMatches() {

    VariablesExpression expression = VariablesExpression.builder()
        .name("status")
        .operator(VaribalesOperator.EQUALS)
        .value("ACTIVE")
        .build();

    TaskInstanceFilter filter = TaskInstanceFilter.builder()
        .variablesExpressions(new ArrayList<>(List.of(expression)))
        .build();

    when(runtimeProcessEngineRepository.getAllProcessInstancesByVariables(filter.getVariablesExpressions()))
        .thenReturn(List.of());

    PageableLista<TaskInstance> page = new PageableLista<>(0, 50, 0L, 0, true, true, List.of());
    when(taskInstanceRepository.findAll(filter)).thenReturn(page);

    taskInstanceService.getAllTaskInstances(filter);

    // No process-variable matches: the repository falls back to the task-local (JSONB) predicate only.
    verify(runtimeProcessEngineRepository).getAllProcessInstancesByVariables(filter.getVariablesExpressions());
    assertTrue(filter.getEngineProcessNumbers().isEmpty());
  }

}
