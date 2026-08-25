package cv.igrp.platform.process.management.area.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.area.domain.service.AreaService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


@Component
public class RemoveProcessDefinitionCommandHandler implements CommandHandler<RemoveProcessDefinitionCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoveProcessDefinitionCommandHandler.class);

  private final AreaService areaService;

  public RemoveProcessDefinitionCommandHandler(AreaService areaService) {
    this.areaService = areaService;
  }

  @IgrpCommandHandler
  public ResponseEntity<String> handle(RemoveProcessDefinitionCommand command) {
    LOGGER.debug("Removing process definition");
    areaService.removeProcessDefinition(
        UUID.fromString(command.getAreaId()),
        UUID.fromString(command.getProcessDefinitionId())
    );
    LOGGER.info("Process definition removed");
    return ResponseEntity.status(204).build();
  }

}
