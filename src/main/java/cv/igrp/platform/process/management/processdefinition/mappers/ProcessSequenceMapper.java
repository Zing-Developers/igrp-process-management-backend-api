package cv.igrp.platform.process.management.processdefinition.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.processdefinition.application.commands.CreateProcessSequenceCommand;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessSequenceDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessSequence;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceSequenceEntity;
import org.springframework.stereotype.Component;

@Component
public class ProcessSequenceMapper {


  public ProcessSequence toModel(CreateProcessSequenceCommand command) {
    return ProcessSequence.builder()
        .processDefinitionKey(Code.create(command.getProcessDefinitionKey()))
        .name(Name.create(command.getSequencerequestdto().getName()))
        .prefix(Code.create(command.getSequencerequestdto().getPrefix()))
        .dateFormat(command.getSequencerequestdto().getDateFormat())
        .padding(command.getSequencerequestdto().getPadding())
        .checkDigitSize(command.getSequencerequestdto().getCheckDigitSize())
        .numberIncrement(command.getSequencerequestdto().getNumberIncrement())
        .separator(command.getSequencerequestdto().getSeparator())
        .build();
  }


  public ProcessInstanceSequenceEntity toEntity(ProcessSequence model) {
    var entity = new ProcessInstanceSequenceEntity();
    entity.setId(model.getId().getValue());
    entity.setName(model.getName().getValue());
    entity.setPrefix(model.getPrefix().getValue());
    entity.setCheckDigitSize(model.getCheckDigitSize());
    entity.setPadding(model.getPadding());
    entity.setDateFormat(model.getDateFormat());
    entity.setNextNumber(model.getNextNumber());
    entity.setNumberIncrement(model.getNumberIncrement());
    entity.setProcessDefinitionKey(model.getProcessDefinitionKey().getValue());
    entity.setSeparator(model.getSeparator());
    return  entity;
  }


  public ProcessSequence toModel(ProcessInstanceSequenceEntity entity) {
    var model = ProcessSequence.builder()
        .id(Identifier.create(entity.getId()))
        .name(Name.create(entity.getName()))
        .prefix(Code.create(entity.getPrefix()))
        .checkDigitSize(entity.getCheckDigitSize())
        .padding(entity.getPadding())
        .dateFormat(entity.getDateFormat())
        .nextNumber(entity.getNextNumber())
        .numberIncrement(entity.getNumberIncrement())
        .processDefinitionKey(Code.create(entity.getProcessDefinitionKey()))
        .separator(entity.getSeparator())
        .build();
    model.setAudit(AuditMapping.trail(entity));
    return model;
  }


  public ProcessSequenceDTO toDTO(ProcessSequence model) {
    var dto = new ProcessSequenceDTO();
    dto.setId(model.getId().getValue());
    dto.setName(model.getName().getValue());
    dto.setPrefix(model.getPrefix().getValue());
    dto.setCheckDigitSize(model.getCheckDigitSize());
    dto.setPadding(model.getPadding());
    dto.setDateFormat(model.getDateFormat());
    dto.setNextNumber(model.getNextNumber());
    dto.setNumberIncrement(model.getNumberIncrement());
    dto.setProcessDefinitionKey(model.getProcessDefinitionKey().getValue());
    dto.setSeparator(model.getSeparator());
    AuditMapping.apply(dto, model.getAudit());
    return dto;
  }

  public ProcessSequence toModel(ProcessSequenceDTO processSequenceDTO) {
    return ProcessSequence.builder()
        .processDefinitionKey(Code.create(processSequenceDTO.getProcessDefinitionKey()))
        .name(Name.create(processSequenceDTO.getName()))
        .prefix(Code.create(processSequenceDTO.getPrefix()))
        .dateFormat(processSequenceDTO.getDateFormat())
        .padding(processSequenceDTO.getPadding())
        .checkDigitSize(processSequenceDTO.getCheckDigitSize())
        .numberIncrement(processSequenceDTO.getNumberIncrement())
        .build();
  }

}
