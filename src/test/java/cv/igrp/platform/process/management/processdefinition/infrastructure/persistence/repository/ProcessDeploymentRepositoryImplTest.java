package cv.igrp.platform.process.management.processdefinition.infrastructure.persistence.repository;

import cv.igrp.framework.process.runtime.core.engine.process.ProcessDefinitionAdapter;
import cv.igrp.framework.process.runtime.core.engine.process.ProcessManagerAdapter;
import cv.igrp.framework.process.runtime.core.engine.process.model.IgrpProcessDefinitionRepresentation;
import cv.igrp.framework.process.runtime.core.engine.process.model.ProcessDefinition;
import cv.igrp.framework.process.runtime.core.engine.process.model.ProcessFilter;
import cv.igrp.framework.process.runtime.core.engine.task.TaskQueryService;
import cv.igrp.platform.process.management.processdefinition.domain.exception.ProcessDeploymentException;
import cv.igrp.platform.process.management.processdefinition.domain.filter.ProcessDeploymentFilter;
import cv.igrp.platform.process.management.processdefinition.domain.models.BpmnXml;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessDeployment;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessDeploymentMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.domain.models.ResourceName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDeploymentRepositoryImplTest {

  @Mock
  private ProcessDefinitionAdapter processDefinitionAdapter;

  @Mock
  private ProcessManagerAdapter processManagerAdapter;

  private ProcessDeploymentRepositoryImpl repository;

  private ProcessDeployment deployment;

  @Mock
  private TaskQueryService taskQueryService;

  @BeforeEach
  void setUp() {
    ProcessDeploymentMapper processDeploymentMapper = new ProcessDeploymentMapper();
    repository = new ProcessDeploymentRepositoryImpl(
        processDefinitionAdapter,
        processDeploymentMapper
    );
    deployment = ProcessDeployment.builder()
        .key(Code.create("deployment_process_key"))
        .name(Name.create("Invoicing sample"))
        .resourceName(ResourceName.create("invoicing.bpmn20.xml"))
        .bpmnXml(BpmnXml.create("<definitions />"))
        .applicationBase(Code.create("igrp-app"))
        .build();
  }

  @Test
  void deploy_shouldReturnMappedModel_whenAdapterSucceeds() {

    // Arrange
    IgrpProcessDefinitionRepresentation representation =
        IgrpProcessDefinitionRepresentation.builder()
            .key("deployment_process_key")
            .name("Invoicing sample")
            .resourceName("invoicing.bpmn20.xml")
            .bpmnXml("<definitions />")
            .applicationBase("igrp-app")
            .deploymentId("12345678")
            .version("1.0")
            .deployed(true)
            .build();

    when(processDefinitionAdapter.deploy(any(IgrpProcessDefinitionRepresentation.class)))
        .thenReturn(representation);

    // Act
    ProcessDeployment result = repository.deploy(deployment);

    // Assert
    assertNotNull(result);
    assertEquals("deployment_process_key", result.getKey().getValue());
    assertEquals("Invoicing sample", result.getName().getValue());
    assertEquals("invoicing.bpmn20.xml", result.getResourceName().getValue());
    assertEquals("12345678", result.getDeploymentId());
    assertTrue(result.isDeployed());

    verify(processDefinitionAdapter).deploy(any(IgrpProcessDefinitionRepresentation.class));

  }

  @Test
  void deploy_shouldThrowProcessDeploymentException_whenAdapterThrows() {

    // When
    when(processDefinitionAdapter.deploy(any(IgrpProcessDefinitionRepresentation.class)))
        .thenThrow(new RuntimeException("Deployment failed"));

    // Act
    ProcessDeploymentException ex = assertThrows(
        ProcessDeploymentException.class,
        () -> repository.deploy(deployment)
    );

    // asserts
    assertTrue(ex.getMessage().contains("Failed to deploy process deployment: " + deployment.getKey().getValue()));

    verify(processDefinitionAdapter).deploy(any(IgrpProcessDefinitionRepresentation.class));
  }

  @Test
  void getAllDeployments_shouldReturnMappedList() {

    // Arrange
    ProcessDeploymentFilter filter = ProcessDeploymentFilter.builder()
        .build();

    ProcessDefinition processDefinition = new ProcessDefinition(
        "12345",
        "Invoicing Process",
        "invoicing.bpmn20.xml",
        "invoice_process_key",
        1,
        "98765",
        "Invoicing Process Description",
        "igrp-app",
        false
    );

    when(processDefinitionAdapter.getDeployedProcesses(any(ProcessFilter.class)))
        .thenReturn(List.of(processDefinition));

    // Act
    PageableLista<ProcessDeployment> result = repository.findAll(filter);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getContent().size());

    ProcessDeployment item = result.getContent().getFirst();

    assertEquals(processDefinition.key(), item.getKey().getValue());
    assertEquals(processDefinition.name(), item.getName().getValue());
    assertEquals(processDefinition.description(), item.getDescription());
    assertEquals(processDefinition.applicationBase(), item.getApplicationBase().getValue());
    assertEquals(processDefinition.resourceName(), item.getResourceName().getValue());
    assertEquals(String.valueOf(processDefinition.version()), item.getVersion());
    assertEquals(processDefinition.deploymentId(), item.getDeploymentId());
    assertTrue(item.isDeployed());

    verify(processDefinitionAdapter).getDeployedProcesses(any(ProcessFilter.class));

  }

  @Test
  void findAllArtifacts_shouldReturnMappedArtifacts() {
    // Arrange
    String processDefinitionId = "123456789";
    cv.igrp.framework.process.runtime.core.engine.task.model.ProcessArtifact artifact1 =
        new cv.igrp.framework.process.runtime.core.engine.task.model.ProcessArtifact(
            "task_1",
            "Task 1",
            "/path/to/form/task_1"
        );

    when(processDefinitionAdapter.getProcessArtifacts(processDefinitionId))
        .thenReturn(List.of(artifact1));

    // Act
    List<ProcessArtifact> result = repository.findAllArtifacts(processDefinitionId);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());

    ProcessArtifact actualArtifact = result.getFirst();
    assertEquals("task_1", actualArtifact.getKey().getValue());
    assertEquals("/path/to/form/task_1", actualArtifact.getFormKey());
    assertEquals("Task 1", actualArtifact.getName().getValue());
    assertEquals(processDefinitionId, actualArtifact.getProcessDefinitionId().getValue());

    verify(processDefinitionAdapter).getProcessArtifacts(processDefinitionId);

  }

}
