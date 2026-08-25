package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.application.dto.ProcessEventDTO;
import cv.igrp.platform.process.management.shared.application.dto.ProcessVariableDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class TriggerProcessEventCommandHandler implements CommandHandler<TriggerProcessEventCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TriggerProcessEventCommandHandler.class);

  private final ProcessInstanceService processInstanceService;

  public TriggerProcessEventCommandHandler(ProcessInstanceService processInstanceService) {
    this.processInstanceService = processInstanceService;
  }

  @IgrpCommandHandler
  public ResponseEntity<String> handle(TriggerProcessEventCommand command) {

    ProcessEventDTO event = command.getProcesseventdto();
    if (event == null || event.getBusinessKey() == null || event.getBusinessKey().isBlank()) {
      LOGGER.warn("Invalid or incomplete event message");
      return ResponseEntity.badRequest().body("Invalid or incomplete event message");
    }

    Map<String, Object> vars = event.getVariables().stream()
        .filter(v -> v.getName() != null)
        .collect(Collectors.toMap(
            ProcessVariableDTO::getName,
            ProcessVariableDTO::getValue,
            (a, b) -> b
        ));

    try {

      if (event.getMessageName() != null && !event.getMessageName().isBlank()) {
        LOGGER.debug("Correlating message '{}' for businessKey '{}'", event.getMessageName(), event.getBusinessKey());
        processInstanceService.correlateMessage(event.getBusinessKey(), event.getMessageName(), vars);
      } else {
        LOGGER.debug("Signaling process instance for businessKey '{}'", event.getBusinessKey());
        processInstanceService.signal(event.getBusinessKey(), event.getTaskId(), vars);
      }

      LOGGER.info("Processed event successfully for businessKey: {}", event.getBusinessKey());

      return ResponseEntity.ok().body("Processed event successfully for businessKey: " + event.getBusinessKey());

    } catch (Exception e) {
      LOGGER.error("Error while processing event for businessKey: {}", event.getBusinessKey(), e);
      return ResponseEntity.internalServerError().body("Error while processing event for businessKey: " + event.getBusinessKey());
    }

  }

}
