package cv.igrp.platform.process.management.processdefinition.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processdefinition.domain.service.TaskPriorityService;
import cv.igrp.platform.process.management.processdefinition.mappers.TaskPriorityMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.process.management.processdefinition.application.dto.TaskPriorityDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConfigurePriorityCommandHandler implements CommandHandler<ConfigurePriorityCommand, ResponseEntity<TaskPriorityDTO>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurePriorityCommandHandler.class);

  private final TaskPriorityService taskPriorityService;
  private final TaskPriorityMapper mapper;

  public ConfigurePriorityCommandHandler(TaskPriorityService taskPriorityService,
                                         TaskPriorityMapper mapper) {
    this.taskPriorityService = taskPriorityService;
    this.mapper = mapper;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<TaskPriorityDTO> handle(ConfigurePriorityCommand command) {
    LOGGER.debug("Configuring priority for process [{}]", command.getProcessKey());
    taskPriorityService.configurePriority(mapper.toModel(
        command.getProcessKey(),
        command.getTaskpriorityrequestdto()
    ));
    LOGGER.info("Priority configured for process [{}]", command.getProcessKey());
    return ResponseEntity.status(204).build();
  }

}
