package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleUpdateDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskAssignmentRuleMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTaskAssignmentRuleCommandHandlerTest {

  @Mock
  private TaskAssignmentRuleService service;

  @Mock
  private TaskAssignmentRuleMapper mapper;

  @InjectMocks
  private UpdateTaskAssignmentRuleCommandHandler handler;

  @Test
  void handle_shouldUpdateRuleAndReturnDTO() {
    String id = UUID.randomUUID().toString();
    var request = new TaskAssignmentRuleUpdateDTO("demo@nosi.cv", "user1@nosi.cv");
    var command = new UpdateTaskAssignmentRuleCommand(request, id);
    var assignee = Code.create("demo@nosi.cv");
    var candidateUsers = Set.of("user1@nosi.cv");
    var rule = TaskAssignmentRule.builder()
        .processDefinitionKey(Code.create("process-a"))
        .taskDefinitionKey(Code.create("task-a"))
        .assignee(assignee)
        .candidateUsers(candidateUsers)
        .build();
    var dto = new TaskAssignmentRuleListDTO();

    when(mapper.toAssignee(request)).thenReturn(assignee);
    when(mapper.toCandidateUsers(request)).thenReturn(candidateUsers);
    when(service.updateAssignment(id, assignee, candidateUsers)).thenReturn(rule);
    when(mapper.toListDTO(rule)).thenReturn(dto);

    var response = handler.handle(command);

    assertEquals(200, response.getStatusCodeValue());
    assertSame(dto, response.getBody());
    verify(service).updateAssignment(id, assignee, candidateUsers);
  }
}
