package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.framework.process.runtime.core.engine.process.model.IGRPProcessStatus;
import cv.igrp.platform.process.management.processruntime.application.dto.*;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessStatistics;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleRequest;
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.application.dto.StartProcessDTO;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProcessInstanceMapper {

  private final UserProfileMapper userProfileMapper;

  public ProcessInstanceMapper(UserProfileMapper userProfileMapper) {
    this.userProfileMapper = userProfileMapper;
  }

  public ProcessInstanceEntity toEntity(ProcessInstance processInstance) {
    ProcessInstanceEntity processInstanceEntity = new ProcessInstanceEntity();
    processInstanceEntity.setNumber(processInstance.getNumber() != null ? processInstance.getNumber().getValue() : null);
    processInstanceEntity.setEngineProcessNumber(processInstance.getEngineProcessNumber() != null ? processInstance.getEngineProcessNumber().getValue() : null);
    processInstanceEntity.setId(processInstance.getId().getValue());
    processInstanceEntity.setProcReleaseId(processInstance.getProcReleaseId().getValue());
    processInstanceEntity.setProcReleaseKey(processInstance.getProcReleaseKey().getValue());
    processInstanceEntity.setApplicationBase(processInstance.getApplicationBase()!=null ? processInstance.getApplicationBase().getValue() : null);
    processInstanceEntity.setVersion(processInstance.getVersion());
    processInstanceEntity.setStatus(processInstance.getStatus());
    processInstanceEntity.setBusinessKey(processInstance.getBusinessKey() != null ? processInstance.getBusinessKey().getValue() : null);
    processInstanceEntity.setCanceledAt(processInstance.getCanceledAt());
    processInstanceEntity.setCanceledBy(processInstance.getCanceledBy());
    processInstanceEntity.setEndedAt(processInstance.getEndedAt());
    processInstanceEntity.setEndedBy(processInstance.getEndedBy());
    processInstanceEntity.setStartedAt(processInstance.getStartedAt());
    processInstanceEntity.setStartedBy(processInstance.getStartedBy());
    processInstanceEntity.setObsCancel(processInstance.getObsCancel());
    processInstanceEntity.setName(processInstance.getName());
    processInstanceEntity.setPriority(processInstance.getPriority());
    processInstanceEntity.setIsArchived(processInstance.isArchived());
    return processInstanceEntity;
  }

  public void updateEntityFromModel(ProcessInstance processInstance, ProcessInstanceEntity processInstanceEntity) {
    processInstanceEntity.setNumber(processInstance.getNumber() != null ? processInstance.getNumber().getValue() : null);
    processInstanceEntity.setEngineProcessNumber(processInstance.getEngineProcessNumber() != null ? processInstance.getEngineProcessNumber().getValue() : null);
    processInstanceEntity.setProcReleaseId(processInstance.getProcReleaseId().getValue());
    processInstanceEntity.setProcReleaseKey(processInstance.getProcReleaseKey().getValue());
    processInstanceEntity.setApplicationBase(processInstance.getApplicationBase()!=null ? processInstance.getApplicationBase().getValue() : null);
    processInstanceEntity.setVersion(processInstance.getVersion());
    processInstanceEntity.setStatus(processInstance.getStatus());
    processInstanceEntity.setBusinessKey(processInstance.getBusinessKey() != null ? processInstance.getBusinessKey().getValue() : null);
    processInstanceEntity.setCanceledAt(processInstance.getCanceledAt());
    processInstanceEntity.setCanceledBy(processInstance.getCanceledBy());
    processInstanceEntity.setEndedAt(processInstance.getEndedAt());
    processInstanceEntity.setEndedBy(processInstance.getEndedBy());
    processInstanceEntity.setStartedAt(processInstance.getStartedAt());
    processInstanceEntity.setStartedBy(processInstance.getStartedBy());
    processInstanceEntity.setObsCancel(processInstance.getObsCancel());
    processInstanceEntity.setName(processInstance.getName());
    processInstanceEntity.setPriority(processInstance.getPriority());
  }

  public ProcessInstance toModel(ProcessInstanceEntity processInstanceEntity) {
    var model = ProcessInstance.builder()
        .id(Identifier.create(processInstanceEntity.getId()))
        .name(processInstanceEntity.getName())
        .number(processInstanceEntity.getNumber() != null ? ProcessNumber.create(processInstanceEntity.getNumber()) : null)
        .engineProcessNumber(processInstanceEntity.getEngineProcessNumber()!=null?Code.create(processInstanceEntity.getEngineProcessNumber()):null)
        .procReleaseKey(Code.create(processInstanceEntity.getProcReleaseKey()))
        .businessKey(processInstanceEntity.getBusinessKey() != null ? Code.create(processInstanceEntity.getBusinessKey()) : null)
        .procReleaseId(Code.create(processInstanceEntity.getProcReleaseId()))
        .status(processInstanceEntity.getStatus())
        .version(processInstanceEntity.getVersion())
        .startedAt(processInstanceEntity.getStartedAt())
        .endedAt(processInstanceEntity.getEndedAt())
        .canceledAt(processInstanceEntity.getCanceledAt())
        .startedBy(processInstanceEntity.getStartedBy())
        .endedBy(processInstanceEntity.getEndedBy())
        .canceledBy(processInstanceEntity.getCanceledBy())
        .obsCancel(processInstanceEntity.getObsCancel())
        .applicationBase(processInstanceEntity.getApplicationBase()!=null ? Code.create(processInstanceEntity.getApplicationBase()) : null)
        .priority(processInstanceEntity.getPriority())
        .build();
    model.setAudit(AuditMapping.trail(processInstanceEntity));
    return model;
  }

  public ProcessInstance toModel(StartProcessRequestDTO startProcessRequestDTO) {
    Map<String, Object> vars = new HashMap<>();
    startProcessRequestDTO.getVariables().forEach(variable -> {
      vars.put(variable.getName(), variable.getValue());
    });
    return ProcessInstance.builder()
        .procReleaseId(startProcessRequestDTO.getProcessDefinitionId() != null ? Code.create(startProcessRequestDTO.getProcessDefinitionId()) : null)
        .procReleaseKey(Code.create(startProcessRequestDTO.getProcessKey()))
        .businessKey(startProcessRequestDTO.getBusinessKey() != null ? Code.create(startProcessRequestDTO.getBusinessKey()) : null)
        .applicationBase(startProcessRequestDTO.getApplicationBase()!= null ? Code.create(startProcessRequestDTO.getApplicationBase()) : null)
        .variables(vars)
        .assignmentRules(toAssignmentRules(startProcessRequestDTO.getAssignmentRules()))
        .priority(startProcessRequestDTO.getPriority())
        .build();
  }

  public List<TaskAssignmentRuleRequest> toAssignmentRules(List<ProcessTaskAssignmentRuleDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .filter(dto -> dto.getTaskKey() != null && !dto.getTaskKey().isBlank())
        .map(dto -> TaskAssignmentRuleRequest.builder()
            .taskKey(Code.create(dto.getTaskKey().trim()))
            .assignee(dto.getAssignee() != null && !dto.getAssignee().isBlank()
                ? Code.create(dto.getAssignee().trim())
                : null)
            .candidateUsers(splitCommaSeparated(dto.getCandidateUsers()))
            .candidateGroups(splitCommaSeparated(dto.getCandidateGroups()))
            .assignmentMode(dto.getAssignmentMode())
            .priority(dto.getPriority())
            .build())
        .toList();
  }

  private List<String> splitCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
  }

  public ProcessInstanceDTO toDTO(ProcessInstance processInstance) {
    ProcessInstanceDTO processInstanceDTO = new ProcessInstanceDTO();
    processInstanceDTO.setId(processInstance.getId().getValue());
    processInstanceDTO.setNumber(processInstance.getNumber() != null ? processInstance.getNumber().getValue() : null);
    processInstanceDTO.setProcReleaseId(processInstance.getProcReleaseId().getValue());
    processInstanceDTO.setProcReleaseKey(processInstance.getProcReleaseKey().getValue());
    processInstanceDTO.setVersion(processInstance.getVersion());
    processInstanceDTO.setStatus(processInstance.getStatus());
    processInstanceDTO.setStatusDesc(processInstance.getStatus().getDescription());
    processInstanceDTO.setStartedAt(processInstance.getStartedAt());
    processInstanceDTO.setStartedBy(processInstance.getStartedBy());
    processInstanceDTO.setCanceledAt(processInstance.getCanceledAt());
    processInstanceDTO.setCancelledBy(processInstance.getCanceledBy());
    processInstanceDTO.setEndedAt(processInstance.getEndedAt());
    processInstanceDTO.setEndedBy(processInstance.getEndedBy());
    processInstanceDTO.setObsCancel(processInstance.getObsCancel());
    processInstanceDTO.setApplicationBase(processInstance.getApplicationBase()!= null ? processInstance.getApplicationBase().getValue(): null);
    processInstanceDTO.setBusinessKey(processInstance.getBusinessKey() != null ? processInstance.getBusinessKey().getValue() : null);
    processInstanceDTO.setName(processInstance.getName());
    processInstanceDTO.setProgress(processInstance.getProgress());
    processInstanceDTO.setPriority(processInstance.getPriority());
    processInstanceDTO.setVariables(processInstance.getVariables().entrySet()
        .stream().map(e->new ProcessVariableDTO(e.getKey(), e.getValue())).toList());
    processInstanceDTO.setUserProfileCancelledBy(
        userProfileMapper.toDTO(processInstance.getUserProfileCanceledBy())
    );
    processInstanceDTO.setUserProfileStartedBy(
        userProfileMapper.toDTO(processInstance.getUserProfileStartedBy())
    );
    processInstanceDTO.setUserProfileEndedBy(
        userProfileMapper.toDTO(processInstance.getUserProfileEndedBy())
    );
    AuditMapping.apply(processInstanceDTO, processInstance.getAudit());
    return processInstanceDTO;
  }

  public ProcessInstanceListPageDTO toDTO(PageableLista<ProcessInstance> processInstances) {
    ProcessInstanceListPageDTO dto = new ProcessInstanceListPageDTO();
    dto.setTotalElements(processInstances.getTotalElements());
    dto.setTotalPages(processInstances.getTotalPages());
    dto.setPageNumber(processInstances.getPageNumber());
    dto.setPageSize(processInstances.getPageSize());
    dto.setFirst(processInstances.isFirst());
    dto.setLast(processInstances.isLast());
    List<ProcessInstanceDTO> content = processInstances.getContent()
        .stream()
        .map(this::toDTO)
        .toList();
    dto.setContent(content);
    return dto;
  }

  public ProcessInstance toModel(cv.igrp.framework.process.runtime.core.engine.process.model.ProcessInstance processInstance) {

    if (processInstance == null) {
      return null;
    }

    return ProcessInstance.builder()
        .engineProcessNumber(Code.create(processInstance.id()))
        .procReleaseId(processInstance.processDefinitionId() != null ? Code.create(processInstance.processDefinitionId()) : null)
        .procReleaseKey(processInstance.processDefinitionKey() != null ? Code.create(processInstance.processDefinitionKey()) : null)
        .businessKey(processInstance.businessKey() != null ? Code.create(processInstance.businessKey()) : null)
        .applicationBase(Code.create("igrp")) // ou mapeia de algum lado se houver contexto
        .version(processInstance.processDefinitionVersion() != null ? String.valueOf(processInstance.processDefinitionVersion()) : null)
        .startedBy(processInstance.initiator())
        .startedAt(Optional.ofNullable(processInstance.startDate())
            .map(d -> d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .orElse(null))
        .endedAt(Optional.ofNullable(processInstance.completedDate())
            .map(d -> d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .orElse(null))
        .status(mapStatus(processInstance.status()))
        .variables(new HashMap<>()) // se houver alguma origem para as variáveis, mapeia aqui
        .name(processInstance.name() != null ? processInstance.name()  :  processInstance.processDefinitionName())
        .build();
  }

  private ProcessInstanceStatus mapStatus(IGRPProcessStatus igrpStatus) {
    if (igrpStatus == null) return ProcessInstanceStatus.CREATED;
    try {
      return ProcessInstanceStatus.valueOf(igrpStatus.name());
    } catch (IllegalArgumentException ex) {
      return ProcessInstanceStatus.CREATED; // fallback
    }
  }


  public ProcessInstanceStatsDTO toProcessInstanceStatsDto(ProcessStatistics statistics) {
    return new ProcessInstanceStatsDTO(
        statistics.getTotalProcessInstances(),
        statistics.getTotalCreatedProcess(),
        statistics.getTotalRunningProcess(),
        statistics.getTotalCompletedProcess(),
        statistics.getTotalSuspendedProcess(),
        statistics.getTotalCanceledProcess()
    );
  }

  public ProcessInstance toModel(CreateProcessRequestDTO createProcessRequestDTO) {
    return ProcessInstance.builder()
        .procReleaseId(createProcessRequestDTO.getProcessDefinitionId() != null ? Code.create(createProcessRequestDTO.getProcessDefinitionId()) : null)
        .procReleaseKey(Code.create(createProcessRequestDTO.getProcessKey()))
        .businessKey(createProcessRequestDTO.getBusinessKey() != null ? Code.create(createProcessRequestDTO.getBusinessKey()) : null)
        .applicationBase(createProcessRequestDTO.getApplicationBase()!= null ? Code.create(createProcessRequestDTO.getApplicationBase()) : null)
        .priority(createProcessRequestDTO.getPriority())
        .build();
  }

  public ProcessInstance toModel(StartProcessDTO dto) {
    Map<String, Object> vars = dto.getVariables().stream()
        .filter(v -> v.getName() != null)
        .collect(Collectors.toMap(
            cv.igrp.platform.process.management.shared.application.dto.ProcessVariableDTO::getName,
            cv.igrp.platform.process.management.shared.application.dto.ProcessVariableDTO::getValue,
            (a, b) -> b
        ));
    return ProcessInstance.builder()
        .procReleaseId(dto.getProcessDefinitionId() != null ? Code.create(dto.getProcessDefinitionId()) : null)
        .procReleaseKey(Code.create(dto.getProcessKey()))
        .businessKey(dto.getBusinessKey() != null ? Code.create(dto.getBusinessKey()) : null)
        .applicationBase(Code.create(dto.getApplicationBase()))
        .variables(vars)
        .priority(dto.getPriority())
        .build();
  }

}
