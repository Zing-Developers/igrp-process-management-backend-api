package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.*;
import cv.igrp.platform.process.management.processruntime.domain.repository.*;
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.*;

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
  private RuntimeProcessEngineRepository runtimeProcessEngineRepository;

  @Mock
  private ProcessInstanceRepository processInstanceRepository;

  @Mock
  private ProcessDefinitionRepository processDefinitionRepository;

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

    Map<String, Object> forms = Map.of("name", "Maria");
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

}
