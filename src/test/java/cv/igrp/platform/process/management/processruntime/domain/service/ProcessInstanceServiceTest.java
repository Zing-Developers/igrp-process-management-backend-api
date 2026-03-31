package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessSequenceService;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstanceTaskStatus;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessStatistics;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.RuntimeProcessEngineRepository;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

  @InjectMocks
  private ProcessInstanceService processInstanceService;

  private String currentUser;


  @BeforeEach
  void setup() {
    currentUser = "demo@nosi.cv";
    processInstanceService = spy(new ProcessInstanceService(
        processInstanceRepository,
        runtimeProcessEngineRepository,
        processSequenceService,
        taskInstanceService,
        processDeploymentService,
        userProfileRepository
    ));
  }



  @Test
  void getAllTaskInstances_shouldReturnPageableList() {

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

    assertEquals(expected, result);
    verify(processInstanceRepository, times(1)).findAll(filter);

  }



  @Test
  void getProcessInstanceById_shouldReturnTaskInstance_whenFound() {
    UUID id = UUID.randomUUID();
    final var mockProcessInstance = mock(ProcessInstance.class);
    when(mockProcessInstance.getEngineProcessNumber())
        .thenReturn(Code.create(UUID.randomUUID().toString()));

    when(processInstanceRepository.findById(id))
        .thenReturn(Optional.of(mockProcessInstance));

    ProcessInstance result = processInstanceService.getProcessInstanceById(id);

    assertNotNull(result);
    assertSame(mockProcessInstance, result);
    verify(processInstanceService, times(1))
        .setProcessInstanceProgress(mockProcessInstance);
    verify(processInstanceService, times(1))
        .addProcessVariables(mockProcessInstance);
  }

  @Test
  void getProcessInstanceById_shouldThrowException_whenNotFound() {
    UUID id = UUID.randomUUID();

    when(processInstanceRepository.findById(id))
        .thenReturn(Optional.empty());

    IgrpResponseStatusException ex = assertThrows(
        IgrpResponseStatusException.class,
        () -> processInstanceService.getProcessInstanceById(id)
    );
    assertTrue(ex.getMessage().contains("No process instance found with id:"));
    assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
  }



  // TODO: startProcessInstance is now private. These tests need to be rewritten
  // to test through the public API (createAndStartProcessInstance or startProcessInstanceById).



  @Test
  void testGetProcessInstanceTaskStatus() {

    var processId = UUID.randomUUID();
    ProcessInstance mockInstance = mock(ProcessInstance.class);
    Code mockEngProcNumber = mock(Code.class);

    when(mockEngProcNumber.getValue()).thenReturn("PROC-123");
    when(mockInstance.getEngineProcessNumber()).thenReturn(mockEngProcNumber);

    doReturn(mockInstance).when(processInstanceService).getProcessInstanceById(processId);

    // mock of external repository
    List<ProcessInstanceTaskStatus> mockStatus = List.of(
        ProcessInstanceTaskStatus.builder().taskKey(Code.create("Task1"))
            .taskName(Name.create("Task1Name")).status(TaskInstanceStatus.CREATED)
            .processInstanceId(Code.create(processId.toString())).build(),
        ProcessInstanceTaskStatus.builder().taskKey(Code.create("Task2"))
            .taskName(Name.create("Task2Name")).status(TaskInstanceStatus.COMPLETED)
            .processInstanceId(Code.create(processId.toString())).build()
    );
    when(runtimeProcessEngineRepository.getProcessInstanceTaskStatus("PROC-123"))
        .thenReturn(mockStatus);

    // act
    List<ProcessInstanceTaskStatus> result = processInstanceService.getProcessInstanceTaskStatus(processId);

    // asserts
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("Task1Name", result.get(0).getTaskName().getValue());
    assertEquals(TaskInstanceStatus.CREATED, result.get(0).getStatus());
    assertEquals("Task2Name", result.get(1).getTaskName().getValue());
    assertEquals(TaskInstanceStatus.COMPLETED, result.get(1).getStatus());

    // verify
    verify(processInstanceService).getProcessInstanceById(processId);
    verify(runtimeProcessEngineRepository).getProcessInstanceTaskStatus("PROC-123");

  }



  @Test
  void testGetProcessStatisticsByUser(){

    ProcessStatistics mockStats = ProcessStatistics.builder()
        .totalProcessInstances(100L)
        .totalCreatedProcess(52L)
        .totalRunningProcess(18L)
        .totalCompletedProcess(5L)
        .totalSuspendedProcess(25L)
        .totalCanceledProcess(7L)
        .build();

    when(processInstanceRepository.getProcessInstanceStatistics()).thenReturn(mockStats);

    ProcessStatistics stats = processInstanceService.getProcessInstanceStatistics();

    assertNotNull(stats);
    assertEquals(100L, stats.getTotalProcessInstances());
    assertEquals(52L, stats.getTotalCreatedProcess());
    assertEquals(18L, stats.getTotalRunningProcess());
    assertEquals(5L, stats.getTotalCompletedProcess());
    assertEquals(25L, stats.getTotalSuspendedProcess());
    assertEquals(7L, stats.getTotalCanceledProcess());

    verify(processInstanceRepository).getProcessInstanceStatistics();

  }

}
