package cv.igrp.platform.process.management.processdefinition.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessDeploymentRequestDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.BpmnXml;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessDeployment;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessDeploymentMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.ResourceName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class DeployProcessCommandHandlerTest {

  @Mock
  private ProcessDeploymentService processDeploymentService;

  DeployProcessCommandHandler handler;

  @BeforeEach
  void setup() {
    ProcessDeploymentMapper mapper = new ProcessDeploymentMapper();
    handler = new DeployProcessCommandHandler(processDeploymentService, mapper);
  }

  @Test
  void testHandle_deploysProcessAndReturnsDTO() {

    ProcessDeploymentRequestDTO requestDTO = new ProcessDeploymentRequestDTO();
    requestDTO.setName("Process invoicing");
    requestDTO.setDescription("Process invoicing description");
    requestDTO.setKey("invoicing_key");
    requestDTO.setResourceName("invoicing.bpmn20.xml");
    requestDTO.setBpmnXml("<definitions...</<definitions>");
    requestDTO.setApplicationBase("igrp-app");

    DeployProcessCommand command = new DeployProcessCommand(requestDTO);

    ProcessDeployment deployedModel = ProcessDeployment.builder()
        .name(Name.create("Process invoicing"))
        .description("Process invoicing description")
        .key(Code.create("invoicing_key"))
        .resourceName(ResourceName.create("invoicing.bpmn20.xml"))
        .bpmnXml(BpmnXml.create("<definitions...</<definitions>"))
        .applicationBase(Code.create("igrp-app"))
        .version("1.0")
        .deploymentId("deploy-001")
        .deployed(true)
        .deployedAt(LocalDateTime.now())
        .build();

    when(processDeploymentService.deployProcessAndConfigure(any(ProcessDeployment.class)))
        .thenReturn(deployedModel);

    // Act
    ResponseEntity<ProcessDeploymentDTO> response = handler.handle(command);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());

    ProcessDeploymentDTO body = response.getBody();
    assertNotNull(body);
    assertEquals(deployedModel.getKey().getValue(), body.getKey());
    assertEquals(deployedModel.getName().getValue(), body.getName());
    assertEquals(deployedModel.getDescription(), body.getDescription());
    assertEquals(deployedModel.getResourceName().getValue(), body.getResourceName());
    assertEquals(deployedModel.getVersion(), body.getVersion());
    assertEquals(deployedModel.getApplicationBase().getValue(), body.getApplicationBase());
    assertEquals(deployedModel.getDeploymentId(), body.getDeploymentId());
    assertEquals(deployedModel.getDeployedAt(), body.getDeployedAt());
    assertTrue(body.isDeployed());

    // Verify service call
    verify(processDeploymentService).deployProcessAndConfigure(any(ProcessDeployment.class));

  }

}
