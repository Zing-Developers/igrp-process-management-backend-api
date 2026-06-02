package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessSequenceService;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstanceTaskStatus;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessStatistics;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleRequest;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.RuntimeProcessEngineRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceServiceTest {

  @Mock
  private RuntimeProcessEngineRepository runtimeProcessEngineRepository;

  @Mock
  private ProcessInstanceRepository processInstanceRepository;

  @Mock
  private ProcessSequenceService processSequenceService;

  @Mock
  private TaskInstanceService taskInstanceService;

  @Mock
  private ProcessDeploymentService processDeploymentService;

  @Mock
  private UserProfileRepository userProfileRepository;

  private ProcessInstanceService processInstanceService;

  @BeforeEach
  void setup() {
    processInstanceService = new ProcessInstanceService(
        processInstanceRepository,
        runtimeProcessEngineRepository,
        processSequenceService,
        taskInstanceService,
        processDeploymentService,
        userProfileRepository
    );
  }

  @Test
  void getAllProcessInstances_shouldReturnRepositoryPage() {
    ProcessInstanceFilter filter = ProcessInstanceFilter.builder().page(0).size(10).build();
    PageableLista<ProcessInstance> expected = PageableLista.<ProcessInstance>builder()
        .pageNumber(0)
        .pageSize(10)
        .totalElements(0L)
        .totalPages(0)
        .content(List.of())
        .first(true)
        .last(true)
        .build();

    when(processInstanceRepository.findAll(filter)).thenReturn(expected);

    PageableLista<ProcessInstance> result = processInstanceService.getAllProcessInstances(filter);

    assertSame(expected, result);
    verify(processInstanceRepository).findAll(filter);
  }

  @Test
  void getProcessInstanceById_shouldEnrichAndReturnProcessInstance_whenFound() {
    UUID id = UUID.randomUUID();
    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.create(id))
        .procReleaseKey(Code.create("process-key"))
        .procReleaseId(Code.create("release-1"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("business-key"))
        .name("Invoice Process")
        .build();

    when(processInstanceRepository.findById(id)).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceTaskStatus("ENG-PROC-123"))
        .thenReturn(List.of());
    when(runtimeProcessEngineRepository.getProcessVariables("ENG-PROC-123"))
        .thenReturn(Map.of("amount", 100));
    when(userProfileRepository.findBySubjectOrEmails(any(), any()))
        .thenReturn(List.of());

    ProcessInstance result = processInstanceService.getProcessInstanceById(id);

    assertSame(processInstance, result);
    assertEquals(100, result.getVariables().get("amount"));
    verify(runtimeProcessEngineRepository).getProcessInstanceTaskStatus("ENG-PROC-123");
    verify(runtimeProcessEngineRepository).getProcessVariables("ENG-PROC-123");
  }

  @Test
  void getProcessInstanceById_shouldThrowException_whenNotFound() {
    UUID id = UUID.randomUUID();
    when(processInstanceRepository.findById(id)).thenReturn(Optional.empty());

    IgrpResponseStatusException ex = assertThrows(
        IgrpResponseStatusException.class,
        () -> processInstanceService.getProcessInstanceById(id)
    );

    assertTrue(ex.getMessage().contains("No process instance found with id:"));
    assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
  }

  @Test
  void createProcessInstance_shouldCreateEngineInstanceAndPersistDomainInstance() {
    ProcessInstance processInstance = mock(ProcessInstance.class);
    ProcessInstance engineProcessInstance = mock(ProcessInstance.class);
    ProcessNumber processNumber = ProcessNumber.create("PROC-123");

    when(processInstance.getProcReleaseId()).thenReturn(null);
    when(processInstance.getProcReleaseKey()).thenReturn(Code.create("invoice_process"));
    when(processInstance.getBusinessKey()).thenReturn(Code.create("business-key"));
    when(processDeploymentService.findLastProcessDefinitionIdByKey("invoice_process"))
        .thenReturn("release-1");
    when(runtimeProcessEngineRepository.createProcessInstanceById("release-1", "business-key"))
        .thenReturn(engineProcessInstance);
    when(processSequenceService.getGeneratedProcessNumber(Code.create("invoice_process")))
        .thenReturn(processNumber);
    when(processInstanceRepository.save(processInstance)).thenReturn(processInstance);

    ProcessInstance result = processInstanceService.createProcessInstance(processInstance, "demo@nosi.cv");

    assertSame(processInstance, result);
    verify(processInstance).create(processNumber, engineProcessInstance, "demo@nosi.cv");
    verify(processInstanceRepository).save(processInstance);
  }

  @Test
  void startProcessInstanceById_shouldStartProcessCreateTasksAndPersistStatus() {
    UUID id = UUID.randomUUID();
    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.create(id))
        .procReleaseKey(Code.create("invoice_process"))
        .procReleaseId(Code.create("release-1"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("business-key"))
        .status(ProcessInstanceStatus.CREATED)
        .name("Invoice Process")
        .build();
    ProcessInstance engineProcess = ProcessInstance.builder()
        .procReleaseKey(Code.create("invoice_process"))
        .status(ProcessInstanceStatus.RUNNING)
        .build();

    when(processInstanceRepository.findById(id)).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceTaskStatus("ENG-PROC-123"))
        .thenReturn(List.of());
    when(runtimeProcessEngineRepository.getProcessVariables("ENG-PROC-123"))
        .thenReturn(Map.of());
    when(userProfileRepository.findBySubjectOrEmails(any(), any()))
        .thenReturn(List.of());
    when(runtimeProcessEngineRepository.startProcessInstanceById(
        eq("ENG-PROC-123"),
        eq("release-1"),
        eq("business-key"),
        anyMap()
    )).thenReturn(engineProcess);
    when(processInstanceRepository.save(processInstance)).thenReturn(processInstance);

    ProcessInstance result = processInstanceService.startProcessInstanceById(
        id,
        Map.of("amount", 100),
        "demo@nosi.cv"
    );

    assertSame(processInstance, result);
    assertEquals(ProcessInstanceStatus.RUNNING, result.getStatus());
    assertEquals(100, result.getVariables().get("amount"));
    verify(taskInstanceService).createTaskInstancesByProcess(processInstance);
    verify(processInstanceRepository).save(processInstance);
  }

  @Test
  void startProcessInstanceById_shouldCarryAssignmentRulesToCreatedTasks() {
    UUID id = UUID.randomUUID();
    TaskAssignmentRuleRequest assignmentRule = TaskAssignmentRuleRequest.builder()
        .taskKey(Code.create("approve-task"))
        .assignee(Code.create("approver@nosi.cv"))
        .build();

    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.create(id))
        .procReleaseKey(Code.create("invoice_process"))
        .procReleaseId(Code.create("release-1"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("business-key"))
        .status(ProcessInstanceStatus.CREATED)
        .name("Invoice Process")
        .build();
    ProcessInstance engineProcess = ProcessInstance.builder()
        .procReleaseKey(Code.create("invoice_process"))
        .status(ProcessInstanceStatus.RUNNING)
        .build();

    when(processInstanceRepository.findById(id)).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceTaskStatus("ENG-PROC-123"))
        .thenReturn(List.of());
    when(runtimeProcessEngineRepository.getProcessVariables("ENG-PROC-123"))
        .thenReturn(Map.of());
    when(userProfileRepository.findBySubjectOrEmails(any(), any()))
        .thenReturn(List.of());
    when(runtimeProcessEngineRepository.startProcessInstanceById(
        eq("ENG-PROC-123"),
        eq("release-1"),
        eq("business-key"),
        anyMap()
    )).thenReturn(engineProcess);
    when(processInstanceRepository.save(processInstance)).thenReturn(processInstance);

    ProcessInstance result = processInstanceService.startProcessInstanceById(
        id,
        Map.of(),
        List.of(assignmentRule),
        "demo@nosi.cv"
    );

    assertSame(processInstance, result);
    assertEquals(List.of(assignmentRule), result.getAssignmentRules());
    verify(taskInstanceService).registerAssignmentRules(processInstance);
    verify(taskInstanceService).createTaskInstancesByProcess(processInstance);
  }

  @Test
  void signal_shouldCarryAssignmentRulesToCreatedTasks() {
    TaskAssignmentRuleRequest assignmentRule = TaskAssignmentRuleRequest.builder()
        .taskKey(Code.create("next-task"))
        .assignee(Code.create("owner@nosi.cv"))
        .build();
    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.generate())
        .procReleaseKey(Code.create("invoice_process"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("business-key"))
        .build();
    ProcessInstance engineProcess = ProcessInstance.builder()
        .procReleaseKey(Code.create("invoice_process"))
        .status(ProcessInstanceStatus.RUNNING)
        .build();

    when(processInstanceRepository.findByBusinessKey("business-key")).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceById("ENG-PROC-123")).thenReturn(engineProcess);
    when(processInstanceRepository.save(processInstance)).thenReturn(processInstance);

    processInstanceService.signal("business-key", "task-1", Map.of("approved", true), List.of(assignmentRule));

    assertEquals(List.of(assignmentRule), processInstance.getAssignmentRules());
    verify(runtimeProcessEngineRepository).signal("ENG-PROC-123", "task-1", Map.of("approved", true));
    verify(taskInstanceService).createTaskInstancesByProcess(processInstance);
  }

  @Test
  void correlateMessage_shouldCarryAssignmentRulesToCreatedTasks() {
    TaskAssignmentRuleRequest assignmentRule = TaskAssignmentRuleRequest.builder()
        .taskKey(Code.create("next-task"))
        .assignee(Code.create("owner@nosi.cv"))
        .build();
    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.generate())
        .procReleaseKey(Code.create("invoice_process"))
        .engineProcessNumber(Code.create("ENG-PROC-123"))
        .businessKey(Code.create("business-key"))
        .build();
    ProcessInstance engineProcess = ProcessInstance.builder()
        .procReleaseKey(Code.create("invoice_process"))
        .status(ProcessInstanceStatus.RUNNING)
        .build();

    when(processInstanceRepository.findByBusinessKey("business-key")).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceById("ENG-PROC-123")).thenReturn(engineProcess);
    when(processInstanceRepository.save(processInstance)).thenReturn(processInstance);

    processInstanceService.correlateMessage("business-key", "message-a", Map.of("approved", true), List.of(assignmentRule));

    assertEquals(List.of(assignmentRule), processInstance.getAssignmentRules());
    verify(runtimeProcessEngineRepository).correlateMessage("message-a", "business-key", Map.of("approved", true));
    verify(taskInstanceService).createTaskInstancesByProcess(processInstance);
  }

  @Test
  void getProcessInstanceTaskStatus_shouldDelegateUsingEngineProcessNumber() {
    UUID processId = UUID.randomUUID();
    ProcessInstance processInstance = ProcessInstance.builder()
        .id(Identifier.create(processId))
        .procReleaseKey(Code.create("process-key"))
        .engineProcessNumber(Code.create("PROC-123"))
        .build();
    List<ProcessInstanceTaskStatus> statuses = List.of(
        ProcessInstanceTaskStatus.builder()
            .taskKey(Code.create("Task1"))
            .taskName(Name.create("Task1Name"))
            .status(TaskInstanceStatus.CREATED)
            .processInstanceId(Code.create(processId.toString()))
            .build()
    );

    when(processInstanceRepository.findById(processId)).thenReturn(Optional.of(processInstance));
    when(runtimeProcessEngineRepository.getProcessInstanceTaskStatus("PROC-123"))
        .thenReturn(statuses);
    when(runtimeProcessEngineRepository.getProcessVariables("PROC-123")).thenReturn(Map.of());
    when(userProfileRepository.findBySubjectOrEmails(any(), any())).thenReturn(List.of());

    List<ProcessInstanceTaskStatus> result = processInstanceService.getProcessInstanceTaskStatus(processId);

    assertEquals(statuses, result);
    verify(runtimeProcessEngineRepository, org.mockito.Mockito.times(2))
        .getProcessInstanceTaskStatus("PROC-123");
  }

  @Test
  void getProcessInstanceStatistics_shouldDelegateToRepository() {
    ProcessStatistics stats = ProcessStatistics.builder()
        .totalProcessInstances(100L)
        .totalCreatedProcess(52L)
        .totalRunningProcess(18L)
        .totalCompletedProcess(5L)
        .totalSuspendedProcess(25L)
        .totalCanceledProcess(7L)
        .build();

    when(processInstanceRepository.getProcessInstanceStatistics()).thenReturn(stats);

    ProcessStatistics result = processInstanceService.getProcessInstanceStatistics();

    assertNotNull(result);
    assertEquals(100L, result.getTotalProcessInstances());
    verify(processInstanceRepository).getProcessInstanceStatistics();
  }
}
