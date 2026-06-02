package cv.igrp.platform.process.management.processdefinition.infrastructure.persistence.repository;

import cv.igrp.framework.process.runtime.core.engine.process.ProcessDefinitionAdapter;
import cv.igrp.framework.process.runtime.core.engine.process.ProcessDefinitionRepresentation;
import cv.igrp.framework.process.runtime.core.engine.process.model.IgrpProcessDefinitionRepresentation;
import cv.igrp.framework.process.runtime.core.engine.process.model.ProcessDefinition;
import cv.igrp.framework.process.runtime.core.engine.process.model.ProcessFilter;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDeploymentRepositoryImplTest {

  @Mock
  private ProcessDefinitionAdapter processDefinitionAdapter;

  private ProcessDeploymentRepositoryImpl repository;
  private ProcessDeployment deployment;

  @BeforeEach
  void setUp() {
    repository = new ProcessDeploymentRepositoryImpl(
        processDefinitionAdapter,
        new ProcessDeploymentMapper()
    );

    deployment = ProcessDeployment.builder()
        .key(Code.create("invoice_process"))
        .name(Name.create("Invoice Process"))
        .resourceName(ResourceName.create("invoice.bpmn20.xml"))
        .bpmnXml(BpmnXml.create("<definitions />"))
        .applicationBase(Code.create("igrp-app"))
        .build();
  }

  @Test
  void deploy_shouldSendRepresentationToAdapterAndReturnMappedDeployment() {
    IgrpProcessDefinitionRepresentation deployedRepresentation = IgrpProcessDefinitionRepresentation.builder()
        .key("invoice_process")
        .name("Invoice Process")
        .resourceName("invoice.bpmn20.xml")
        .bpmnXml("<definitions />")
        .applicationBase("igrp-app")
        .releaseId("release-1")
        .deploymentId("deployment-1")
        .version("1")
        .deployed(true)
        .build();

    when(processDefinitionAdapter.deploy(any(ProcessDefinitionRepresentation.class)))
        .thenReturn(deployedRepresentation);

    ProcessDeployment result = repository.deploy(deployment);

    assertNotNull(result);
    assertEquals("release-1", result.getId());
    assertEquals("invoice_process", result.getKey().getValue());
    assertEquals("Invoice Process", result.getName().getValue());
    assertEquals("invoice.bpmn20.xml", result.getResourceName().getValue());
    assertEquals("deployment-1", result.getDeploymentId());
    assertTrue(result.isDeployed());

    ArgumentCaptor<ProcessDefinitionRepresentation> captor =
        ArgumentCaptor.forClass(ProcessDefinitionRepresentation.class);
    verify(processDefinitionAdapter).deploy(captor.capture());

    IgrpProcessDefinitionRepresentation sent =
        (IgrpProcessDefinitionRepresentation) captor.getValue();
    assertEquals("invoice_process", sent.key());
    assertEquals("Invoice Process", sent.name());
    assertEquals("invoice.bpmn20.xml", sent.resourceName());
    assertEquals("<definitions />", sent.bpmnXml());
    assertEquals("igrp-app", sent.applicationBase());
  }

  @Test
  void deploy_shouldWrapAdapterFailureInDomainException() {
    when(processDefinitionAdapter.deploy(any(ProcessDefinitionRepresentation.class)))
        .thenThrow(new RuntimeException("engine unavailable"));

    ProcessDeploymentException exception = assertThrows(
        ProcessDeploymentException.class,
        () -> repository.deploy(deployment)
    );

    assertTrue(exception.getMessage().contains("invoice_process"));
    verify(processDefinitionAdapter).deploy(any(ProcessDefinitionRepresentation.class));
  }

  @Test
  void findAll_shouldMapDefinitionsAndForwardFilterValues() {
    ProcessDeploymentFilter filter = ProcessDeploymentFilter.builder()
        .processName("Invoice")
        .applicationBase(Code.create("igrp-app"))
        .groups(Set.of("finance", "approver"))
        .isSuspended(false)
        .build();

    ProcessDefinition processDefinition = new ProcessDefinition(
        "release-1",
        "Invoice Process",
        "invoice.bpmn20.xml",
        "invoice_process",
        3,
        "deployment-1",
        "Invoice approval flow",
        "igrp-app",
        false
    );

    when(processDefinitionAdapter.getDeployedProcesses(any(ProcessFilter.class)))
        .thenReturn(List.of(processDefinition));

    PageableLista<ProcessDeployment> result = repository.findAll(filter);

    assertNotNull(result);
    assertEquals(1, result.getContent().size());

    ProcessDeployment item = result.getContent().getFirst();
    assertEquals("release-1", item.getId());
    assertEquals("release-1", item.getProcReleaseId().getValue());
    assertEquals("invoice_process", item.getKey().getValue());
    assertEquals("Invoice Process", item.getName().getValue());
    assertEquals("Invoice approval flow", item.getDescription());
    assertEquals("igrp-app", item.getApplicationBase().getValue());
    assertEquals("invoice.bpmn20.xml", item.getResourceName().getValue());
    assertEquals("3", item.getVersion());
    assertEquals("deployment-1", item.getDeploymentId());
    assertTrue(item.isDeployed());

    ArgumentCaptor<ProcessFilter> captor = ArgumentCaptor.forClass(ProcessFilter.class);
    verify(processDefinitionAdapter).getDeployedProcesses(captor.capture());

    ProcessFilter sentFilter = captor.getValue();
    assertEquals("Invoice", sentFilter.getName());
    assertEquals("igrp-app", sentFilter.getApplicationBase());
    assertFalse(sentFilter.getSuspended());
    assertTrue(sentFilter.getGroupsIds().containsAll(List.of("finance", "approver")));
  }

  @Test
  void findAll_shouldUseContextGroupsWhenFilteringByCurrentUser() {
    ProcessDeploymentFilter filter = ProcessDeploymentFilter.builder()
        .filterByCurrentUser(true)
        .contextGroups(Set.of("context-group"))
        .groups(Set.of("client-group"))
        .build();

    when(processDefinitionAdapter.getDeployedProcesses(any(ProcessFilter.class)))
        .thenReturn(List.of());

    repository.findAll(filter);

    ArgumentCaptor<ProcessFilter> captor = ArgumentCaptor.forClass(ProcessFilter.class);
    verify(processDefinitionAdapter).getDeployedProcesses(captor.capture());

    assertEquals(List.of("context-group"), captor.getValue().getGroupsIds());
  }

  @Test
  void findAllArtifacts_shouldMapEngineArtifacts() {
    String processDefinitionId = "release-1";
    cv.igrp.framework.process.runtime.core.engine.task.model.ProcessArtifact engineArtifact =
        new cv.igrp.framework.process.runtime.core.engine.task.model.ProcessArtifact(
            "task_1",
            "Approve invoice",
            "approve-invoice-form"
        );

    when(processDefinitionAdapter.getProcessArtifacts(processDefinitionId))
        .thenReturn(List.of(engineArtifact));

    List<ProcessArtifact> result = repository.findAllArtifacts(processDefinitionId);

    assertEquals(1, result.size());
    assertEquals("task_1", result.getFirst().getKey().getValue());
    assertEquals("Approve invoice", result.getFirst().getName().getValue());
    assertEquals("approve-invoice-form", result.getFirst().getFormKey());
    assertEquals(processDefinitionId, result.getFirst().getProcessDefinitionId().getValue());
  }

  @Test
  void findById_shouldMapProcessDefinitionRepresentationWhenPresent() {
    IgrpProcessDefinitionRepresentation representation = IgrpProcessDefinitionRepresentation.builder()
        .key("invoice_process")
        .name("Invoice Process")
        .description("Invoice approval flow")
        .resourceName("invoice.bpmn20.xml")
        .bpmnXml("<definitions />")
        .applicationBase("igrp-app")
        .deploymentId("deployment-1")
        .version("2")
        .build();

    when(processDefinitionAdapter.getProcessDefinition("release-1"))
        .thenReturn(Optional.of(representation));

    Optional<ProcessDeployment> result = repository.findById("release-1");

    assertTrue(result.isPresent());
    assertEquals("invoice_process", result.get().getKey().getValue());
    assertEquals("Invoice Process", result.get().getName().getValue());
    assertEquals("Invoice approval flow", result.get().getDescription());
    assertEquals("igrp-app", result.get().getApplicationBase().getValue());
    assertEquals("deployment-1", result.get().getDeploymentId());
  }

  @Test
  void starterGroupOperations_shouldDelegateToAdapter() {
    when(processDefinitionAdapter.getCandidateStarterGroups("release-1"))
        .thenReturn(List.of("finance", "approver"));

    repository.addCandidateStarterGroup("release-1", "finance");
    repository.removeCandidateStarterGroup("release-1", "approver");
    Set<String> groups = repository.getCandidateStarterGroups("release-1");

    assertEquals(Set.of("finance", "approver"), groups);
    verify(processDefinitionAdapter).addCandidateStarterGroup("release-1", "finance");
    verify(processDefinitionAdapter).removeCandidateStarterGroup("release-1", "approver");
    verify(processDefinitionAdapter).getCandidateStarterGroups("release-1");
  }
}
