package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskOperationData;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskInstanceService;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Component
public class ClaimTaskCommandHandler implements CommandHandler<ClaimTaskCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClaimTaskCommandHandler.class);

  private final TaskInstanceService taskInstanceService;
  private final UserContext userContext;

  public ClaimTaskCommandHandler(TaskInstanceService taskInstanceService, UserContext userContext) {
    this.taskInstanceService = taskInstanceService;
    this.userContext = userContext;
  }

  @IgrpCommandHandler
  @Transactional
  public ResponseEntity<String> handle(ClaimTaskCommand command) {
    final var currentUser = userContext.getCurrentUser();
    LOGGER.debug("User [{}] started claiming task [{}]", currentUser.getValue(), command.getId());
    taskInstanceService.claimTask(
        TaskOperationData.builder()
            .currentUser(currentUser)
            .id(command.getId())
            .build()
    );
    LOGGER.info("User [{}] finished claiming task [{}]", currentUser.getValue(), command.getId());
    return ResponseEntity.noContent().build();
  }

}
