package cv.igrp.platform.process.management.processdefinition.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessArtifactDTO;
import cv.igrp.platform.process.management.processdefinition.application.dto.ProcessArtifactRequestDTO;
import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessArtifactEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProcessArtifactMapper {

  public ProcessArtifact toModel(ProcessArtifactRequestDTO dto, String processDefinitionId, String taskKey) {
    return ProcessArtifact.builder()
        .name(Name.create(dto.getName()))
        .processDefinitionId(Code.create(processDefinitionId))
        .key(Code.create(taskKey))
        .formKey(dto.getFormKey())
        .candidateGroups(dto.getCandidateGroups() != null && !dto.getCandidateGroups().isBlank()
            ? new HashSet<>(List.of(dto.getCandidateGroups().split(",")))
            : new HashSet<>())
        .dueDate(dto.getDueDate())
        .priority(dto.getPriority())
        .build();
  }

  public ProcessArtifactDTO toDTO(ProcessArtifact model) {
    ProcessArtifactDTO dto = new ProcessArtifactDTO();
    dto.setId(model.getId().getValue());
    dto.setName(model.getName().getValue());
    dto.setProcessDefinitionId(model.getProcessDefinitionId().getValue());
    dto.setKey(model.getKey().getValue());
    dto.setFormKey(model.getFormKey());
    dto.setCandidateGroups(
        !model.getCandidateGroups().isEmpty() ? String.join(",", model.getCandidateGroups()) : null
    );
    dto.setPriority(model.getPriority());
    dto.setDueDate(model.getDueDate());
    AuditMapping.apply(dto, model.getAudit());
    return dto;
  }

  public ProcessArtifactEntity toEntity(ProcessArtifact model) {
    ProcessArtifactEntity entity = new ProcessArtifactEntity();
    entity.setName(model.getName().getValue());
    entity.setProcessDefinitionId(model.getProcessDefinitionId().getValue());
    entity.setKey(model.getKey().getValue());
    entity.setFormKey(model.getFormKey());
    entity.setId(model.getId().getValue());
    if(!model.getCandidateGroups().isEmpty()) {
      entity.setCandidateGroups(String.join(",", model.getCandidateGroups()));
    }
    entity.setDueDate(model.getDueDate());
    entity.setPriority(model.getPriority());
    return entity;
  }

  public ProcessArtifact toModel(ProcessArtifactEntity entity) {
    var model = ProcessArtifact.builder()
        .id(Identifier.create(entity.getId()))
        .name(Name.create(entity.getName()))
        .processDefinitionId(Code.create(entity.getProcessDefinitionId()))
        .key(Code.create(entity.getKey()))
        .formKey(entity.getFormKey())
        .candidateGroups(entity.getCandidateGroups() != null
            ? new HashSet<>(List.of(entity.getCandidateGroups().split(",")))
            : new HashSet<>())
        .dueDate(entity.getDueDate())
        .priority(entity.getPriority())
        .build();
    model.setAudit(AuditMapping.trail(entity));
    return model;
  }

  public List<ProcessArtifactDTO> toDTO(List<ProcessArtifact> processArtifacts) {
    return processArtifacts.stream().map(this::toDTO).toList();
  }

  public List<ProcessArtifact> toModel(List<ProcessArtifactEntity> entities) {
    return entities.stream().map(this::toModel).toList();
  }

  public ProcessArtifact toModel(ProcessArtifactDTO dto) {
    return ProcessArtifact.builder()
        .id(Identifier.create(dto.getId()))
        .name(Name.create(dto.getName()))
        .processDefinitionId(Code.create(dto.getProcessDefinitionId()))
        .key(Code.create(dto.getKey()))
        .formKey(dto.getFormKey())
        .candidateGroups(
            dto.getCandidateGroups() != null
                ? new HashSet<>(List.of(dto.getCandidateGroups().split(",")))
                : new HashSet<>()
        )
        .build();
  }

}
