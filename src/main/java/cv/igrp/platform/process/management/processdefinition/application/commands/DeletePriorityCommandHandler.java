package cv.igrp.platform.process.management.processdefinition.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processdefinition.domain.service.TaskPriorityService;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class DeletePriorityCommandHandler implements CommandHandler<DeletePriorityCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeletePriorityCommandHandler.class);

  private final TaskPriorityService taskPriorityService;

  public DeletePriorityCommandHandler(TaskPriorityService taskPriorityService) {
    this.taskPriorityService = taskPriorityService;
  }

  @IgrpCommandHandler
  public ResponseEntity<String> handle(DeletePriorityCommand command) {
    LOGGER.debug("Deleting priority [{}]", command.getId());
    taskPriorityService.deletePriority(Identifier.create(command.getId()));
    LOGGER.info("Priority [{}] deleted", command.getId());
    return ResponseEntity.status(204).build();
  }

}
