package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeleteTaskAssignmentRuleCommandHandler
    implements CommandHandler<DeleteTaskAssignmentRuleCommand, ResponseEntity<String>> {

  private final TaskAssignmentRuleService service;

  public DeleteTaskAssignmentRuleCommandHandler(TaskAssignmentRuleService service) {
    this.service = service;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<String> handle(DeleteTaskAssignmentRuleCommand command) {
    service.deactivate(command.getId());
    return ResponseEntity.noContent().build();
  }
}
