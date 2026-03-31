package cv.igrp.platform.process.management.processdefinition.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessArtifactDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessArtifactService;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessArtifactMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.process.management.processdefinition.application.queries.*;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class GetDeployedArtifactsByProcessDefinitionIdQueryHandlerTest {

  @Mock
  private ProcessDeploymentService processDeploymentService;

  private GetDeployedArtifactsByProcessDefinitionIdQueryHandler handler;

  @BeforeEach
  void setUp() {
    ProcessArtifactMapper mapper = new ProcessArtifactMapper();
    handler = new GetDeployedArtifactsByProcessDefinitionIdQueryHandler(processDeploymentService, mapper);
  }

  @Test
  void handle_shouldReturnMappedDTOs() {
    // Arrange
    String processDefinitionId = "12345678";
    GetDeployedArtifactsByProcessDefinitionIdQuery query =
        new GetDeployedArtifactsByProcessDefinitionIdQuery(processDefinitionId);

    ProcessArtifact artifact = ProcessArtifact.builder()
        .id(Identifier.create(UUID.randomUUID()))
        .name(Name.create("Task 1"))
        .processDefinitionId(Code.create(processDefinitionId))
        .key(Code.create("task_1"))
        .formKey("/path/to/form/task_1")
        .build();

    when(processDeploymentService.getDeployedArtifactsByProcessDefinitionId(processDefinitionId))
        .thenReturn(List.of(artifact));

    // Act
    ResponseEntity<List<ProcessArtifactDTO>> response = handler.handle(query);

    // Assert
    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());

    ProcessArtifactDTO dto = response.getBody().getFirst();
    assertEquals(artifact.getId().getValue(), dto.getId());
    assertEquals(artifact.getName().getValue(), dto.getName());
    assertEquals(artifact.getProcessDefinitionId().getValue(), dto.getProcessDefinitionId());
    assertEquals(artifact.getKey().getValue(), dto.getKey());
    assertEquals(artifact.getFormKey(), dto.getFormKey());

    verify(processDeploymentService)
        .getDeployedArtifactsByProcessDefinitionId(processDefinitionId);
  }

}
