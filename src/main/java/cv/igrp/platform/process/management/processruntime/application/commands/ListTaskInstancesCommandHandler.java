package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskInstanceService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskInstanceMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceListPageDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTaskInstancesCommandHandler implements CommandHandler<ListTaskInstancesCommand, ResponseEntity<TaskInstanceListPageDTO>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListTaskInstancesCommandHandler.class);

  private final TaskInstanceService taskInstanceService;
  private final TaskInstanceMapper taskInstanceMapper;

  public ListTaskInstancesCommandHandler(TaskInstanceService taskInstanceService,
                                         TaskInstanceMapper taskInstanceMapper
  ) {
    this.taskInstanceService = taskInstanceService;
    this.taskInstanceMapper = taskInstanceMapper;
  }

  @Transactional(readOnly = true)
  @IgrpCommandHandler
  public ResponseEntity<TaskInstanceListPageDTO> handle(ListTaskInstancesCommand command) {
    final var filter = taskInstanceMapper.toFilter(command);
    final var taskInstances = taskInstanceService.getAllTaskInstances(filter);
    return ResponseEntity.ok(taskInstanceMapper.toTaskInstanceListPageDTO(taskInstances));
  }

}
