package cv.igrp.platform.process.management.processdefinition.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;


@Component
public class ArchiveProcessDefinitionCommandHandler implements CommandHandler<ArchiveProcessDefinitionCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveProcessDefinitionCommandHandler.class);

  private final ProcessDeploymentService processDeploymentService;

  public ArchiveProcessDefinitionCommandHandler(ProcessDeploymentService processDeploymentService) {
    this.processDeploymentService = processDeploymentService;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<String> handle(ArchiveProcessDefinitionCommand command) {
    LOGGER.debug("Archiving process definition [{}]", command.getId());
    processDeploymentService.archiveProcess(command.getId());
    LOGGER.info("Process definition [{}] archived", command.getId());
    return ResponseEntity.status(204).build();
  }

}
