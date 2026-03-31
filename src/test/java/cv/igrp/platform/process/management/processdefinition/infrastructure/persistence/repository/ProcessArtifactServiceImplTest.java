package cv.igrp.platform.process.management.processdefinition.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessArtifactMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessArtifactEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessArtifactEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessArtifactServiceImplTest {

  @Mock
  private ProcessArtifactEntityRepository repository;

  private ProcessDefinitionRepository service;

  private Identifier id;
  private ProcessArtifact artifact;
  private ProcessArtifactEntity entity;
  private String processDefinitionId;

  @BeforeEach
  void setUp() {
    ProcessArtifactMapper mapper = new ProcessArtifactMapper();
    service = new ProcessArtifactServiceImpl(repository, mapper);

    id = Identifier.generate();
    artifact = ProcessArtifact.builder()
        .id(id)
        .name(Name.create("Task 1"))
        .processDefinitionId(Code.create("12345678"))
        .key(Code.create("task_1"))
        .formKey("/path/to/form/task_1")
        .build();

    entity = new ProcessArtifactEntity();
    entity.setId(id.getValue());
    entity.setName("Task 1");
    entity.setProcessDefinitionId("12345678");
    entity.setKey("task_1");
    entity.setFormKey("/path/to/form/task_1");

    processDefinitionId = "12345678";

  }

  @Test
  void saveArtifact_shouldMapToEntity_Save_AndMapBack() {
    // Arrange

    when(repository.save(any(ProcessArtifactEntity.class))).thenReturn(entity);

    // Act
    ProcessArtifact result = service.saveArtifact(artifact);

    // Assert
    assertNotNull(result);
    assertEquals(artifact.getId(), result.getId());
    assertEquals(artifact.getName(), result.getName());
    assertEquals(artifact.getProcessDefinitionId(), result.getProcessDefinitionId());
    assertEquals(artifact.getKey(), result.getKey());
    assertEquals(artifact.getFormKey(), result.getFormKey());

    verify(repository).save(any(ProcessArtifactEntity.class));

  }

  @Test
  void findAllArtifacts_shouldMapListFromEntities() {
    // Arrange

    when(repository.findAllByProcessDefinitionId(processDefinitionId)).thenReturn(List.of(entity));

    // Act
    List<ProcessArtifact> result = service.findAllArtifacts(Code.create(processDefinitionId));

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());

    ProcessArtifact processArtifact = result.getFirst();
    assertEquals(entity.getId(), processArtifact.getId().getValue());
    assertEquals(entity.getName(), processArtifact.getName().getValue());
    assertEquals(entity.getProcessDefinitionId(), processArtifact.getProcessDefinitionId().getValue());
    assertEquals(entity.getKey(), processArtifact.getKey().getValue());
    assertEquals(entity.getFormKey(), processArtifact.getFormKey());

    verify(repository).findAllByProcessDefinitionId(processDefinitionId);

  }

  @Test
  void findArtifactById_found_shouldMapEntityToModel() {
    // Arrange

    when(repository.findById(id.getValue())).thenReturn(Optional.of(entity));

    // Act
    Optional<ProcessArtifact> result = service.findArtifactById(id);

    // Assert
    assertTrue(result.isPresent());
    ProcessArtifact a = result.get();
    assertEquals(entity.getId(), a.getId().getValue());
    assertEquals(entity.getName(), a.getName().getValue());
    assertEquals(entity.getProcessDefinitionId(), a.getProcessDefinitionId().getValue());
    assertEquals(entity.getKey(), a.getKey().getValue());
    assertEquals(entity.getFormKey(), a.getFormKey());

    verify(repository).findById(id.getValue());
  }

  @Test
  void findArtifactById_notFound_shouldReturnEmpty() {

    when(repository.findById(id.getValue())).thenReturn(Optional.empty());

    Optional<ProcessArtifact> result = service.findArtifactById(id);

    assertFalse(result.isPresent());

    verify(repository).findById(id.getValue());
  }

  @Test
  void deleteArtifact_shouldDelegateToRepository() {

    service.deleteArtifact(artifact);

    verify(repository).deleteById(id.getValue());

  }

}
