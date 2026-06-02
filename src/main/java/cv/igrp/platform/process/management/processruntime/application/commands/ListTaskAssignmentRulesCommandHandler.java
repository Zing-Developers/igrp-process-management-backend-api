package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListPageDTO;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskAssignmentRuleService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskAssignmentRuleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ListTaskAssignmentRulesCommandHandler
    implements CommandHandler<ListTaskAssignmentRulesCommand, ResponseEntity<TaskAssignmentRuleListPageDTO>> {

  private final TaskAssignmentRuleService service;
  private final TaskAssignmentRuleMapper mapper;

  public ListTaskAssignmentRulesCommandHandler(
      TaskAssignmentRuleService service,
      TaskAssignmentRuleMapper mapper
  ) {
    this.service = service;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  @IgrpCommandHandler
  public ResponseEntity<TaskAssignmentRuleListPageDTO> handle(ListTaskAssignmentRulesCommand command) {
    var rules = service.getAll(mapper.toFilter(command));
    return ResponseEntity.ok(mapper.toListPageDTO(rules));
  }
}
