package cv.igrp.platform.process.management.processdefinition.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.processdefinition.application.dto.TaskPriorityDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.TaskPriorityRequestDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.TaskPriority;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskPriorityEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskPriorityMapper {

  public TaskPriority toModel(String processDefinitionKey, TaskPriorityRequestDTO dto) {
    return TaskPriority.builder()
        .processDefinitionKey(processDefinitionKey)
        .code(Code.create(dto.getCode()))
        .label(dto.getLabel())
        .weight(dto.getWeight())
        .id(Identifier.create(dto.getId()))
        .color(dto.getColor())
        .build();
  }

  public List<TaskPriority> toModel(String processDefinitionKey, List<TaskPriorityRequestDTO> dtos) {
    return dtos.stream()
        .map(dto -> toModel(processDefinitionKey, dto))
        .toList();
  }

  public TaskPriorityDTO toDTO(TaskPriority model) {
    TaskPriorityDTO dto = new TaskPriorityDTO();
    dto.setCode(model.getCode().getValue());
    dto.setLabel(model.getLabel());
    dto.setWeight(model.getWeight());
    dto.setProcessDefinitionKey(model.getProcessDefinitionKey());
    dto.setId(model.getId().getValue());
    dto.setColor(model.getColor());
    AuditMapping.apply(dto, model.getAudit());
    return dto;
  }

  public TaskPriorityEntity toEntity(TaskPriority model) {
    TaskPriorityEntity entity = new TaskPriorityEntity();
    entity.setProcessDefinitionKey(model.getProcessDefinitionKey());
    entity.setCode(model.getCode().getValue());
    entity.setLabel(model.getLabel());
    entity.setWeight(model.getWeight());
    entity.setId(model.getId().getValue());
    entity.setColor(model.getColor());
    return entity;
  }

  public TaskPriority toModel(TaskPriorityEntity entity) {
    var model = TaskPriority.builder()
        .weight(entity.getWeight())
        .label(entity.getLabel())
        .code(Code.create(entity.getCode()))
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .id(Identifier.create(entity.getId()))
        .color(entity.getColor())
        .build();
    model.setAudit(AuditMapping.trail(entity));
    return model;
  }

  public List<TaskPriorityDTO> toDTO(List<TaskPriority> taskPriorities) {
    return taskPriorities.stream().map(this::toDTO).toList();
  }
}
