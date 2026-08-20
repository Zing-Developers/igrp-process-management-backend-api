package cv.igrp.platform.process.management.processdefinition.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessSequenceDTO;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessSequenceService;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessSequenceMapper;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateProcessSequenceCommandHandler implements CommandHandler<CreateProcessSequenceCommand, ResponseEntity<ProcessSequenceDTO>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateProcessSequenceCommandHandler.class);

  private final ProcessSequenceService processSequenceService;
  private final ProcessSequenceMapper processSequenceMapper;
  private final UserContext userContext;

  public CreateProcessSequenceCommandHandler(ProcessSequenceService processSequenceService, ProcessSequenceMapper processSequenceMapper, UserContext userContext) {

    this.processSequenceService = processSequenceService;
    this.processSequenceMapper = processSequenceMapper;
    this.userContext = userContext;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<ProcessSequenceDTO> handle(CreateProcessSequenceCommand command) {
    final var currentUser = userContext.getCurrentUser();

    LOGGER.debug("User [{}] started creating sequence for processDefinitionKey [{}]", currentUser.getValue(), command.getProcessDefinitionKey());

    var sequenceResp = processSequenceService.save(processSequenceMapper.toModel(command));

    LOGGER.info("User [{}] finished creating sequence for processDefinitionKey [{}]", currentUser.getValue(), command.getProcessDefinitionKey());

    return ResponseEntity.ok(processSequenceMapper.toDTO(sequenceResp));
  }

}
