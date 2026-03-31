package cv.igrp.platform.process.management.processdefinition.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.filter.ProcessDeploymentFilter;
import cv.igrp.platform.process.management.processdefinition.domain.models.BpmnXml;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessDeployment;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDeploymentRepository;
import cv.igrp.platform.process.management.shared.domain.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDeploymentServiceTest {

  @Mock
  private ProcessDeploymentRepository processDeploymentRepository;

  @InjectMocks
  private ProcessDeploymentService service;

  @Test
  void deployProcess_shouldDeployAndCallRepository() {
    // Arrange
    ProcessDeployment processDeployment = ProcessDeployment.builder()
        .key(Code.create("invoice-process-key"))
        .name(Name.create("Invoice Process"))
        .description("Invoice Process sample")
        .resourceName(ResourceName.create("invoicing.bpmn20.xml"))
        .bpmnXml(BpmnXml.create("<definitions>...</definitions>"))
        .applicationBase(Code.create("igrp-app"))
        .build();

    when(processDeploymentRepository.deploy(any(ProcessDeployment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    ProcessDeployment result = service.deployProcess(processDeployment);

    // Asserts
    assertTrue(result.isDeployed(), "Process should be marked as deployed");
    assertNotNull(result.getDeployedAt(), "DeployedAt should be set");

    verify(processDeploymentRepository).deploy(processDeployment);
  }

  @Test
  void getAllDeployments_shouldReturnPageableLista() {
    // Arrange
    ProcessDeploymentFilter filter = ProcessDeploymentFilter.builder()
        .build();

    ProcessDeployment processDeployment = ProcessDeployment.builder()
        .key(Code.create("invoice-process-key"))
        .name(Name.create("Invoice Process"))
        .description("Invoice Process sample")
        .resourceName(ResourceName.create("invoicing.bpmn20.xml"))
        .bpmnXml(BpmnXml.create("<definitions>...</definitions>"))
        .applicationBase(Code.create("igrp-app"))
        .deployed(true)
        .deployedAt(java.time.LocalDateTime.now())
        .build();

    PageableLista<ProcessDeployment> expectedPage = PageableLista.<ProcessDeployment>builder()
        .pageNumber(0)
        .pageSize(10)
        .totalElements(1L)
        .totalPages(1)
        .first(true)
        .last(true)
        .content(List.of(processDeployment))
        .build();

    when(processDeploymentRepository.findAll(filter)).thenReturn(expectedPage);

    // Act
    PageableLista<ProcessDeployment> result = service.getAllDeployments(filter);

    // Assert
    verify(processDeploymentRepository).findAll(filter);

    // Assertions
    assertNotNull(result);
    assertEquals(0, result.getPageNumber());
    assertEquals(10, result.getPageSize());
    assertEquals(1L, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertTrue(result.isFirst());
    assertTrue(result.isLast());
    assertEquals(1, result.getContent().size());

    ProcessDeployment actualProcessDeployment  = result.getContent().getFirst();
    assertEquals(processDeployment, actualProcessDeployment);
    assertEquals(processDeployment.getKey(), actualProcessDeployment.getKey());
    assertEquals(processDeployment.getName(), actualProcessDeployment.getName());
    assertEquals(processDeployment.getDescription(), actualProcessDeployment.getDescription());
    assertEquals(processDeployment.getResourceName(), actualProcessDeployment.getResourceName());
    assertEquals(processDeployment.getBpmnXml(), actualProcessDeployment.getBpmnXml());
    assertEquals(processDeployment.getApplicationBase(), actualProcessDeployment.getApplicationBase());
    assertTrue(actualProcessDeployment.isDeployed());
    assertNotNull(actualProcessDeployment.getDeployedAt());
  }

  @Test
  void shouldReturnArtifactsForProcessDefinitionId() {
    // Arrange
    Code processDefinitionId = Code.create("123456789");

    ProcessArtifact artifact = ProcessArtifact.builder()
        .id(Identifier.generate())
        .name(Name.create("Task 1"))
        .processDefinitionId(processDefinitionId)
        .key(Code.create("task_1"))
        .formKey("/path/to/form/task_1")
        .build();

    when(processDeploymentRepository.findAllArtifacts(processDefinitionId.getValue()))
        .thenReturn(List.of(artifact));

    // Act
    List<ProcessArtifact> result = service.getDeployedArtifactsByProcessDefinitionId(processDefinitionId.getValue());

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    ProcessArtifact actualArtifact = result.getFirst();
    assertEquals(artifact.getId(), actualArtifact.getId());
    assertEquals(artifact.getName(), actualArtifact.getName());
    assertEquals(artifact.getProcessDefinitionId(), actualArtifact.getProcessDefinitionId());
    assertEquals(artifact.getKey(), actualArtifact.getKey());
    assertEquals(artifact.getFormKey(), actualArtifact.getFormKey());

    verify(processDeploymentRepository).findAllArtifacts(processDefinitionId.getValue());

  }

}
