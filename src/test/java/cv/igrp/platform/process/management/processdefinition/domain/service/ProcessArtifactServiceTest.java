package cv.igrp.platform.process.management.processdefinition.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessArtifactServiceTest {

  @Mock
  private ProcessDefinitionRepository processDefinitionRepository;

  @InjectMocks
  private ProcessArtifactService processArtifactService;

  private ProcessArtifact artifact;

  @BeforeEach
  void setUp() {
    artifact = ProcessArtifact.builder()
        .id(Identifier.generate())
        .name(Name.create("Task 1"))
        .processDefinitionId(Code.create("123456789"))
        .key(Code.create("task_1"))
        .formKey("/path/to/form/task_1")
        .build();
  }

  @Test
  void testCreateArtifact() {
    // Arrange

    when(processDefinitionRepository.saveArtifact(artifact)).thenReturn(artifact);

    // Act
    ProcessArtifact result = processArtifactService.create(artifact);

    // Assert
    assertNotNull(result);
    assertEquals(artifact.getKey(), result.getKey());
    assertEquals(artifact.getName(), result.getName());
    assertEquals(artifact.getProcessDefinitionId(), result.getProcessDefinitionId());
    assertEquals(artifact.getId(), result.getId());
    assertEquals(artifact.getFormKey(), result.getFormKey());

    verify(processDefinitionRepository).saveArtifact(artifact);
  }

  @Test
  void testGetArtifactsByProcessDefinitionId() {
    // Arrange
    Code processDefinitionId = Code.create("123456789");

    when(processDefinitionRepository.findAllArtifacts(processDefinitionId))
        .thenReturn(List.of(artifact));

    // Act
    List<ProcessArtifact> result = processArtifactService.getArtifactsByProcessDefinitionId(processDefinitionId);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(processDefinitionRepository).findAllArtifacts(processDefinitionId);
  }

  @Test
  void testDeleteArtifactSuccess() {
    // Arrange
    Identifier id = Identifier.generate();

    when(processDefinitionRepository.findArtifactById(id))
        .thenReturn(Optional.of(artifact));

    // Act
    processArtifactService.deleteArtifact(id);

    // Assert
    verify(processDefinitionRepository).findArtifactById(id);
    verify(processDefinitionRepository).deleteArtifact(artifact);
  }

  @Test
  void testDeleteArtifactNotFound() {
    // Arrange
    Identifier id = Identifier.generate();

    when(processDefinitionRepository.findArtifactById(id))
        .thenReturn(Optional.empty());

    // Act
    IgrpResponseStatusException exception = assertThrows(
        IgrpResponseStatusException.class,
        () -> processArtifactService.deleteArtifact(id)
    );

    // Assert
    assertTrue(exception.getMessage().contains("Process Artifact not found"));
    verify(processDefinitionRepository, never()).deleteArtifact(any());

  }

}
