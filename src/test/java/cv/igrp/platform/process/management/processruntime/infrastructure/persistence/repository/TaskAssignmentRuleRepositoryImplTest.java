package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskAssignmentRuleEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.TaskAssignmentRuleEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentRuleRepositoryImplTest {

  @Mock
  private TaskAssignmentRuleEntityRepository entityRepository;

  @Mock
  private EntityManager entityManager;

  @InjectMocks
  private TaskAssignmentRuleRepositoryImpl repository;

  @Test
  void findAll_shouldApplyPaginationAndMapEntities() {
    UUID ruleId = UUID.randomUUID();
    UUID processInstanceId = UUID.randomUUID();
    UUID createdByTaskId = UUID.randomUUID();
    var entity = new TaskAssignmentRuleEntity();
    entity.setId(ruleId);
    entity.setProcessDefinitionKey("process-a");
    entity.setProcessInstanceId(processInstance(processInstanceId));
    entity.setTaskDefinitionKey("task-a");
    entity.setAssignee("demo@nosi.cv");
    entity.getCandidateUsers().addAll(List.of("user1@nosi.cv", "user2@nosi.cv"));
    entity.setAssignmentMode(TaskAssignmentMode.ALWAYS);
    entity.setPriority(5);
    entity.setConsumed(false);
    entity.setActive(true);
    entity.setCreatedByTask(taskInstance(createdByTaskId));

    var filter = TaskAssignmentRuleFilter.builder()
        .processInstanceId(Identifier.create(processInstanceId))
        .processDefinitionKey(Code.create("process-a"))
        .taskDefinitionKey(Code.create("task-a"))
        .assignee(Code.create("demo@nosi.cv"))
        .candidateUsers(Set.of("user1@nosi.cv"))
        .assignmentMode(TaskAssignmentMode.ALWAYS)
        .consumed(false)
        .active(true)
        .createdByTask(Identifier.create(createdByTaskId))
        .page(1)
        .size(20)
        .build();
    when(entityRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(1, 20), 21));

    var result = repository.findAll(filter);

    assertEquals(21L, result.getTotalElements());
    assertEquals(1, result.getContent().size());
    var rule = result.getContent().get(0);
    assertEquals(ruleId, rule.getId().getValue());
    assertEquals("process-a", rule.getProcessDefinitionKey().getValue());
    assertEquals(processInstanceId, rule.getProcessInstanceId().getValue());
    assertEquals("task-a", rule.getTaskDefinitionKey().getValue());
    assertEquals("demo@nosi.cv", rule.getAssignee().getValue());
    assertTrue(rule.getCandidateUsers().containsAll(List.of("user1@nosi.cv", "user2@nosi.cv")));
    assertEquals(TaskAssignmentMode.ALWAYS, rule.getAssignmentMode());
    assertEquals(5, rule.getPriority());
    assertEquals(createdByTaskId, rule.getCreatedByTask().getValue());

    ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
    verify(entityRepository).findAll(any(Specification.class), pageRequestCaptor.capture());
    assertEquals(1, pageRequestCaptor.getValue().getPageNumber());
    assertEquals(20, pageRequestCaptor.getValue().getPageSize());
  }

  @Test
  void markConsumed_shouldUseManagedTaskReference() {
    UUID ruleId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    var rule = new TaskAssignmentRuleEntity();
    var taskReference = taskInstance(taskId);

    when(entityRepository.findById(ruleId)).thenReturn(Optional.of(rule));
    when(entityManager.getReference(TaskInstanceEntity.class, taskId)).thenReturn(taskReference);

    repository.markConsumed(Identifier.create(ruleId), Identifier.create(taskId));

    assertTrue(rule.getConsumed());
    assertEquals(taskReference, rule.getCreatedByTask());
    verify(entityManager).getReference(TaskInstanceEntity.class, taskId);
    verify(entityRepository).save(rule);
  }

  @Test
  void updateAssignment_shouldUpdateAssigneeAndCandidateUsers_whenRuleIsActiveAndNotConsumed() {
    UUID ruleId = UUID.randomUUID();
    var rule = new TaskAssignmentRuleEntity();
    rule.setId(ruleId);
    rule.setProcessDefinitionKey("process-a");
    rule.setTaskDefinitionKey("task-a");
    rule.setAssignmentMode(TaskAssignmentMode.ONE_TIME);
    rule.setConsumed(false);
    rule.setActive(true);
    rule.setAssignee("old@nosi.cv");
    rule.getCandidateUsers().add("old-user@nosi.cv");

    when(entityRepository.findById(ruleId)).thenReturn(Optional.of(rule));
    when(entityRepository.save(rule)).thenReturn(rule);

    var result = repository.updateAssignment(
        Identifier.create(ruleId),
        Code.create("new@nosi.cv"),
        Set.of("new-user@nosi.cv")
    );

    assertEquals("new@nosi.cv", rule.getAssignee());
    assertTrue(rule.getCandidateUsers().contains("new-user@nosi.cv"));
    assertFalse(rule.getCandidateUsers().contains("old-user@nosi.cv"));
    assertEquals("new@nosi.cv", result.getAssignee().getValue());
    assertTrue(result.getCandidateUsers().contains("new-user@nosi.cv"));
    verify(entityRepository).save(rule);
  }

  @Test
  void deactivate_shouldSetActiveFalse_whenRuleIsActiveAndNotConsumed() {
    UUID ruleId = UUID.randomUUID();
    var rule = new TaskAssignmentRuleEntity();
    rule.setId(ruleId);
    rule.setActive(true);
    rule.setConsumed(false);

    when(entityRepository.findById(ruleId)).thenReturn(Optional.of(rule));

    repository.deactivate(Identifier.create(ruleId));

    assertFalse(rule.getActive());
    verify(entityRepository).save(rule);
  }

  @Test
  void updateAssignment_shouldThrowConflict_whenRuleIsConsumed() {
    UUID ruleId = UUID.randomUUID();
    var rule = new TaskAssignmentRuleEntity();
    rule.setId(ruleId);
    rule.setActive(true);
    rule.setConsumed(true);

    when(entityRepository.findById(ruleId)).thenReturn(Optional.of(rule));

    assertThrows(IgrpResponseStatusException.class, () ->
        repository.updateAssignment(Identifier.create(ruleId), Code.create("new@nosi.cv"), Set.of()));
  }

  @Test
  void deactivate_shouldThrowConflict_whenRuleIsInactive() {
    UUID ruleId = UUID.randomUUID();
    var rule = new TaskAssignmentRuleEntity();
    rule.setId(ruleId);
    rule.setActive(false);
    rule.setConsumed(false);

    when(entityRepository.findById(ruleId)).thenReturn(Optional.of(rule));

    assertThrows(IgrpResponseStatusException.class, () ->
        repository.deactivate(Identifier.create(ruleId)));
  }

  private ProcessInstanceEntity processInstance(UUID id) {
    var processInstance = new ProcessInstanceEntity();
    processInstance.setId(id);
    return processInstance;
  }

  private TaskInstanceEntity taskInstance(UUID id) {
    var taskInstance = new TaskInstanceEntity();
    taskInstance.setId(id);
    return taskInstance;
  }
}
