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
public class UnClaimTaskCommandHandler implements CommandHandler<UnClaimTaskCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UnClaimTaskCommandHandler.class);

  private final TaskInstanceService taskInstanceService;
  private final UserContext userContext;

  public UnClaimTaskCommandHandler(TaskInstanceService taskInstanceService, UserContext userContext) {
    this.taskInstanceService = taskInstanceService;
    this.userContext = userContext;
  }


  @IgrpCommandHandler
  @Transactional
  public ResponseEntity<String> handle(UnClaimTaskCommand command) {
    final var currentUser = userContext.getCurrentUser();
    LOGGER.debug("User [{}] started unclaiming task [{}]", currentUser.getValue(), command.getId());
    taskInstanceService.unClaimTask(
        TaskOperationData.builder()
            .currentUser(currentUser)
            .id(command.getId())
            .note(command.getUnclaimtaskdto() != null ? command.getUnclaimtaskdto().getNote() : null)
            .build()
    );
    LOGGER.info("User [{}] finished unclaiming task [{}]", currentUser.getValue(), command.getId());
    return ResponseEntity.noContent().build();
  }

}
