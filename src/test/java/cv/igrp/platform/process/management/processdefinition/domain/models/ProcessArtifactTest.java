package cv.igrp.platform.process.management.processdefinition.domain.models;

import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessArtifactTest {

  private Identifier id;
  private Code key;
  private Name name;
  private String formKey;
  private Code processDefinitionId;

  @BeforeEach
  void setUp() {
    id = Identifier.generate();
    key = Code.create("task_1");
    name = Name.create("Task 1");
    formKey = "/path/to/form/task_1";
    processDefinitionId = Code.create("123456789");
  }

  @Test
  void shouldCreateProcessArtifactSuccessfully() {

    ProcessArtifact artifact = ProcessArtifact.builder()
        .id(id)
        .name(name)
        .key(key)
        .formKey(formKey)
        .processDefinitionId(processDefinitionId)
        .build();

    assertEquals(id, artifact.getId());
    assertEquals(name, artifact.getName());
    assertEquals(key, artifact.getKey());
    assertEquals(formKey, artifact.getFormKey());
    assertEquals(processDefinitionId, artifact.getProcessDefinitionId());
  }

  @Test
  void shouldGenerateIdWhenNull() {


    ProcessArtifact artifact = ProcessArtifact.builder()
        .id(null) // force null
        .name(name)
        .key(key)
        .formKey(formKey)
        .processDefinitionId(processDefinitionId)
        .build();

    assertNotNull(artifact.getId(), "Id should be auto-generated");
  }

  @Test
  void shouldThrowExceptionWhenNameIsNull() {

    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> ProcessArtifact.builder()
            .name(null)
            .key(key)
            .formKey(formKey)
            .processDefinitionId(processDefinitionId)
            .build()
    );

    assertEquals("The Name of the task cannot be null!", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenKeyIsNull() {

    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> ProcessArtifact.builder()
            .name(name)
            .key(null)
            .formKey(formKey)
            .processDefinitionId(processDefinitionId)
            .build()
    );

    assertEquals("Task Key Id cannot be null!", exception.getMessage());
  }

  @Test
  void shouldUseDefaultValueWhenFormKeyIsNull() {
    ProcessArtifact artifact = ProcessArtifact.builder()
        .name(name)
        .key(key)
        .formKey(null)
        .processDefinitionId(processDefinitionId)
        .build();

    assertEquals(ProcessArtifact.DEFAULT_VALUE, artifact.getFormKey());
    assertFalse(artifact.isFormKeySet());
  }

  @Test
  void shouldThrowExceptionWhenProcessDefinitionIdIsNull() {

    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> ProcessArtifact.builder()
            .name(name)
            .key(key)
            .formKey(formKey)
            .processDefinitionId(null)
            .build()
    );

    assertEquals("ProcessDefinition Id cannot be null!", exception.getMessage());
  }

}
