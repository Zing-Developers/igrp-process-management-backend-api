package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskInstanceService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskInstanceMapper;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceListPageDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetAllMyTasksCommandHandler implements CommandHandler<GetAllMyTasksCommand, ResponseEntity<TaskInstanceListPageDTO>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAllMyTasksCommandHandler.class);

  private final TaskInstanceService taskInstanceService;
  private final TaskInstanceMapper taskInstanceMapper;

  public GetAllMyTasksCommandHandler(TaskInstanceService taskInstanceService,
                                     TaskInstanceMapper taskInstanceMapper
  ) {
    this.taskInstanceService = taskInstanceService;
    this.taskInstanceMapper = taskInstanceMapper;
  }

  @Transactional(readOnly = true)
  @IgrpCommandHandler
  public ResponseEntity<TaskInstanceListPageDTO> handle(GetAllMyTasksCommand command) {
    final var filter = taskInstanceMapper.toFilter(command);
    PageableLista<TaskInstance> taskInstances = taskInstanceService.getAllTaskInstances(filter);
    return ResponseEntity.ok(taskInstanceMapper.toTaskInstanceListPageDTO(taskInstances));
  }

}
