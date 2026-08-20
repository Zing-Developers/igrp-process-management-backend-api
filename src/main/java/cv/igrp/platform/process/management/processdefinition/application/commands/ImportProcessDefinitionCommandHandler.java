package cv.igrp.platform.process.management.processdefinition.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.process.management.processdefinition.domain.service.ProcessDeploymentService;
import cv.igrp.platform.process.management.processdefinition.mappers.ProcessPackageMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;


@Component
public class ImportProcessDefinitionCommandHandler implements CommandHandler<ImportProcessDefinitionCommand, ResponseEntity<String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportProcessDefinitionCommandHandler.class);

  private final ProcessDeploymentService processDeploymentService;
  private final ProcessPackageMapper mapper;

  public ImportProcessDefinitionCommandHandler(ProcessDeploymentService processDeploymentService,
                                               ProcessPackageMapper mapper) {
    this.processDeploymentService = processDeploymentService;
    this.mapper = mapper;
  }

  @Transactional
  @IgrpCommandHandler
  public ResponseEntity<String> handle(ImportProcessDefinitionCommand command) {
    LOGGER.debug("Importing process definition");
    processDeploymentService.importProcessDefinition(
        mapper.toModel(
            command.getProcesspackagedto()
        )
    );
    LOGGER.debug("Import command finished");
    return ResponseEntity.noContent().build();
  }

}
