package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListDTO;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskAssignmentRuleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateTaskAssignmentRuleCommandHandler
    implements CommandHandler<UpdateTaskAssignmentRuleCommand, ResponseEntity<TaskAssignmentRuleListDTO>> {

  private final TaskAssignmentRuleService service;
  private final TaskAssignmentRuleMapper mapper;

  public UpdateTaskAssignmentRuleCommandHandler(
      TaskAssignmentRuleService service,
      TaskAssignmentRuleMapper mapper
  ) {
    this.service = service;
    this.mapper = mapper;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<TaskAssignmentRuleListDTO> handle(UpdateTaskAssignmentRuleCommand command) {
    var rule = service.updateAssignment(
        command.getId(),
        mapper.toAssignee(command.getTaskAssignmentRuleUpdateDTO()),
        mapper.toCandidateUsers(command.getTaskAssignmentRuleUpdateDTO())
    );
    return ResponseEntity.ok(mapper.toListDTO(rule));
  }
}
