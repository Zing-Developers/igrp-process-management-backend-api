package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteTaskAssignmentRuleCommandHandlerTest {

  @Mock
  private TaskAssignmentRuleService service;

  @InjectMocks
  private DeleteTaskAssignmentRuleCommandHandler handler;

  @Test
  void handle_shouldDeactivateRuleAndReturnNoContent() {
    String id = UUID.randomUUID().toString();

    var response = handler.handle(new DeleteTaskAssignmentRuleCommand(id));

    assertEquals(204, response.getStatusCodeValue());
    verify(service).deactivate(id);
  }
}
