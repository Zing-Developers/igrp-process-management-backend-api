package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.platform.process.management.processruntime.application.commands.ListTaskAssignmentRulesCommand;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleUpdateDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAssignmentRuleMapperTest {

  private final TaskAssignmentRuleMapper mapper = new TaskAssignmentRuleMapper();

  @Test
  void toFilter_shouldParseCommandFilters() {
    UUID processInstanceId = UUID.randomUUID();
    UUID createdByTask = UUID.randomUUID();
    var command = new ListTaskAssignmentRulesCommand(
        processInstanceId.toString(),
        "process-a",
        "task-a",
        "demo@nosi.cv",
        "user1@nosi.cv, user2@nosi.cv",
        TaskAssignmentMode.ALWAYS,
        true,
        false,
        createdByTask.toString(),
        2,
        25
    );

    var filter = mapper.toFilter(command);

    assertEquals(processInstanceId, filter.getProcessInstanceId().getValue());
    assertEquals("process-a", filter.getProcessDefinitionKey().getValue());
    assertEquals("task-a", filter.getTaskDefinitionKey().getValue());
    assertEquals("demo@nosi.cv", filter.getAssignee().getValue());
    assertTrue(filter.getCandidateUsers().containsAll(List.of("user1@nosi.cv", "user2@nosi.cv")));
    assertEquals(TaskAssignmentMode.ALWAYS, filter.getAssignmentMode());
    assertEquals(true, filter.getConsumed());
    assertEquals(false, filter.getActive());
    assertEquals(createdByTask, filter.getCreatedByTask().getValue());
    assertEquals(2, filter.getPage());
    assertEquals(25, filter.getSize());
  }

  @Test
  void toListPageDTO_shouldMapRulesAndPagination() {
    UUID ruleId = UUID.randomUUID();
    UUID processInstanceId = UUID.randomUUID();
    UUID createdByTask = UUID.randomUUID();
    var rule = TaskAssignmentRule.builder()
        .id(Identifier.create(ruleId))
        .processDefinitionKey(Code.create("process-a"))
        .processInstanceId(Identifier.create(processInstanceId))
        .taskDefinitionKey(Code.create("task-a"))
        .assignee(Code.create("demo@nosi.cv"))
        .candidateUsers(List.of("user1@nosi.cv", "user2@nosi.cv"))
        .assignmentMode(TaskAssignmentMode.ONE_TIME)
        .priority(7)
        .consumed(true)
        .active(false)
        .createdByTask(Identifier.create(createdByTask))
        .build();
    var page = PageableLista.<TaskAssignmentRule>builder()
        .pageNumber(0)
        .pageSize(10)
        .totalElements(1L)
        .totalPages(1)
        .first(true)
        .last(true)
        .content(List.of(rule))
        .build();

    var result = mapper.toListPageDTO(page);

    assertEquals(1L, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertEquals(0, result.getPageNumber());
    assertEquals(10, result.getPageSize());
    assertEquals(1, result.getContent().size());
    var dto = result.getContent().get(0);
    assertEquals(ruleId, dto.getId());
    assertEquals("process-a", dto.getProcessDefinitionKey());
    assertEquals(processInstanceId, dto.getProcessInstanceId());
    assertEquals("task-a", dto.getTaskDefinitionKey());
    assertEquals("demo@nosi.cv", dto.getAssignee());
    assertEquals("user1@nosi.cv,user2@nosi.cv", dto.getCandidateUsers());
    assertEquals(TaskAssignmentMode.ONE_TIME, dto.getAssignmentMode());
    assertEquals(7, dto.getPriority());
    assertTrue(dto.isConsumed());
    assertEquals(false, dto.isActive());
    assertEquals(createdByTask, dto.getCreatedByTask());
  }

  @Test
  void toAssigneeAndCandidateUsers_shouldParseUpdateDTO() {
    var dto = new TaskAssignmentRuleUpdateDTO(" demo@nosi.cv ", " user1@nosi.cv, user2@nosi.cv ");

    var assignee = mapper.toAssignee(dto);
    var candidateUsers = mapper.toCandidateUsers(dto);

    assertEquals("demo@nosi.cv", assignee.getValue());
    assertTrue(candidateUsers.containsAll(List.of("user1@nosi.cv", "user2@nosi.cv")));
  }

  @Test
  void toAssigneeAndCandidateUsers_shouldReturnEmptyValuesForBlankDTO() {
    var dto = new TaskAssignmentRuleUpdateDTO(" ", " ");

    var assignee = mapper.toAssignee(dto);
    var candidateUsers = mapper.toCandidateUsers(dto);

    assertEquals(null, assignee);
    assertTrue(candidateUsers.isEmpty());
  }
}
