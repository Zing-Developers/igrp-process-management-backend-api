package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.application.dto.ProcessVariablesRequestDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.processruntime.mappers.ProcessInstanceMapper;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.process.management.processruntime.application.dto.ProcessInstanceDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StartProcessInstanceByIdCommandHandler implements CommandHandler<StartProcessInstanceByIdCommand, ResponseEntity<ProcessInstanceDTO>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartProcessInstanceByIdCommandHandler.class);

  private final ProcessInstanceService processInstanceService;
  private final ProcessInstanceMapper mapper;
  private final UserContext userContext;

  public StartProcessInstanceByIdCommandHandler(ProcessInstanceService processInstanceService,
                                                ProcessInstanceMapper mapper,
                                                UserContext userContext) {
    this.processInstanceService = processInstanceService;
    this.mapper = mapper;
    this.userContext = userContext;
  }


  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<ProcessInstanceDTO> handle(StartProcessInstanceByIdCommand command) {
    ProcessInstance processInstance = processInstanceService.startProcessInstanceById(
        UUID.fromString(command.getId()),
        extractVariables(command.getProcessvariablesrequestdto()),
        mapper.toAssignmentRules(command.getProcessvariablesrequestdto().getAssignmentRules()),
        userContext.getCurrentUser().getValue()
    );
    return ResponseEntity.ok(mapper.toDTO(processInstance));
  }

  private Map<String, Object> extractVariables(ProcessVariablesRequestDTO processVariablesRequestDTO) {
    Map<String, Object> vars = new HashMap<>();
    processVariablesRequestDTO.getVariables().forEach(variable -> {
      vars.put(variable.getName(), variable.getValue());
    });
    return vars;
  }

}
