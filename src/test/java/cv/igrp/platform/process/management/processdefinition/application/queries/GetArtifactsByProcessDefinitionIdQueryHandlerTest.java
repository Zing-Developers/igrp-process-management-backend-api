package cv.igrp.platform.process.management.processdefinition.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessArtifactDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessArtifactService;
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

import java.util.Arrays;
import java.util.List;


@ExtendWith(MockitoExtension.class)
public class GetArtifactsByProcessDefinitionIdQueryHandlerTest {

  @Mock
  private ProcessArtifactService processArtifactService;

  @InjectMocks
  private GetArtifactsByProcessDefinitionIdQueryHandler handler;

  @BeforeEach
  void setUp() {
    ProcessArtifactMapper mapper = new ProcessArtifactMapper();
    handler = new GetArtifactsByProcessDefinitionIdQueryHandler(processArtifactService, mapper);
  }

  @Test
  void handle_ShouldReturnListOfDTOs() {
    // Arrange
    String processDefinitionId = "12345678";
    GetArtifactsByProcessDefinitionIdQuery query = new GetArtifactsByProcessDefinitionIdQuery(processDefinitionId);


    ProcessArtifact artifact1 = ProcessArtifact.builder()
        .id(Identifier.generate())
        .name(Name.create("Task 1"))
        .processDefinitionId(Code.create(processDefinitionId))
        .key(Code.create("task_1"))
        .formKey("/path/to/form/task_1")
        .build();

    ProcessArtifact artifact2 = ProcessArtifact.builder()
        .id(Identifier.generate())
        .name(Name.create("Task 2"))
        .processDefinitionId(Code.create(processDefinitionId))
        .key(Code.create("task_2"))
        .formKey("/path/to/form/task_2")
        .build();

    when(processArtifactService.getArtifactsByProcessDefinitionId(Code.create(processDefinitionId)))
        .thenReturn(Arrays.asList(artifact1, artifact2));

    // Act
    ResponseEntity<List<ProcessArtifactDTO>> response = handler.handle(query);

    // Assert
    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());

    ProcessArtifactDTO dto1 = response.getBody().get(0);
    ProcessArtifactDTO dto2 = response.getBody().get(1);

    assertEquals(artifact1.getId().getValue(), dto1.getId());
    assertEquals(artifact1.getName().getValue(), dto1.getName());
    assertEquals(artifact1.getKey().getValue(), dto1.getKey());
    assertEquals(artifact1.getFormKey(), dto1.getFormKey());

    assertEquals(artifact2.getId().getValue(), dto2.getId());
    assertEquals(artifact2.getName().getValue(), dto2.getName());
    assertEquals(artifact2.getKey().getValue(), dto2.getKey());
    assertEquals(artifact2.getFormKey(), dto2.getFormKey());

    verify(processArtifactService)
        .getArtifactsByProcessDefinitionId(Code.create(processDefinitionId));

  }

}
