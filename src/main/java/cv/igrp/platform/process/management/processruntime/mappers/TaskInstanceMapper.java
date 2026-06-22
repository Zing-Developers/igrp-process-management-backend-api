package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.framework.process.runtime.core.engine.task.model.TaskInfo;
import cv.igrp.platform.process.management.processruntime.application.commands.GetAllMyTasksCommand;
import cv.igrp.platform.process.management.processruntime.application.commands.ListTaskInstancesCommand;
import cv.igrp.platform.process.management.processruntime.application.dto.*;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskStatistics;
import cv.igrp.platform.process.management.processruntime.domain.models.VariablesExpression;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.application.constants.VariableTag;
import cv.igrp.platform.process.management.shared.domain.models.*;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import cv.igrp.platform.process.management.shared.util.DateUtil;
import org.springframework.stereotype.Component;

import java.util.*;

import static cv.igrp.platform.process.management.shared.util.DateUtil.utilDateToLocalDateTime;


@Component
public class TaskInstanceMapper {

  private final TaskInstanceEventMapper eventMapper;
  private final UserProfileMapper userProfileMapper;

  public TaskInstanceMapper(TaskInstanceEventMapper eventMapper,
                            UserProfileMapper userProfileMapper
  ) {
    this.eventMapper = eventMapper;
    this.userProfileMapper = userProfileMapper;
  }


  public TaskInstance toModel(TaskInfo taskInfo) {
    return TaskInstance.builder()
        .taskKey(Code.create(taskInfo.taskDefinitionKey()))
        .externalId(Code.create(taskInfo.id()))
        .formKey(taskInfo.formKey()!=null ? Code.create(taskInfo.formKey()) : null)
        .name(Name.create(taskInfo.name() != null ? taskInfo.name() : "NOT SET"))
        .startedAt(utilDateToLocalDateTime.apply(taskInfo.createdTime()))
        .candidateGroups(new HashSet<>(taskInfo.candidateGroups()))
        .build();
  }


  public TaskInstanceEntity toNewTaskEntity(TaskInstance taskInstance) {
    var taskInstanceEntity = new TaskInstanceEntity();
    taskInstanceEntity.setId(taskInstance.getId().getValue());
    taskInstanceEntity.setTaskKey(taskInstance.getTaskKey().getValue());
    taskInstanceEntity.setFormKey(taskInstance.getFormKey()!=null ? taskInstance.getFormKey().getValue() : null);
    taskInstanceEntity.setName(taskInstance.getName().getValue());
    taskInstanceEntity.setExternalId(taskInstance.getExternalId().getValue());
    syncCandidateGroups(taskInstanceEntity, taskInstance.getCandidateGroups());
    syncCandidateUsers(taskInstanceEntity, taskInstance.getCandidateUsers());
    taskInstanceEntity.setPriority(taskInstance.getPriority());
    taskInstanceEntity.setStatus(taskInstance.getStatus());
    taskInstanceEntity.setStartedBy(taskInstance.getStartedBy().getValue());
    taskInstanceEntity.setStartedAt(taskInstance.getStartedAt());
    if(taskInstance.getProcessInstanceId()!=null) {
        var processInstanceEntity = new ProcessInstanceEntity();
        processInstanceEntity.setId(taskInstance.getProcessInstanceId().getValue());
        taskInstanceEntity.setProcessInstanceId(processInstanceEntity);
    }
    taskInstanceEntity.setDueDate(taskInstance.getDueDate());
    return taskInstanceEntity;
  }


  public void toTaskEntity(TaskInstance taskInstance,TaskInstanceEntity taskInstanceEntity) {
    taskInstanceEntity.setStatus(taskInstance.getStatus());
    taskInstanceEntity.setAssignedBy(taskInstance.getAssignedBy() != null ? taskInstance.getAssignedBy().getValue() : null);
    taskInstanceEntity.setAssignedAt(taskInstance.getAssignedAt());
    taskInstanceEntity.setEndedBy(taskInstance.getEndedBy() != null ? taskInstance.getEndedBy().getValue() : null);
    taskInstanceEntity.setEndedAt(taskInstance.getEndedAt());
    taskInstanceEntity.setPriority(taskInstance.getPriority());
    syncCandidateGroups(taskInstanceEntity, taskInstance.getCandidateGroups());
    syncCandidateUsers(taskInstanceEntity, taskInstance.getCandidateUsers());
    taskInstanceEntity.setVariables(taskInstance.getVariables());
    taskInstanceEntity.setForms(taskInstance.getForms());
    taskInstanceEntity.setDueDate(taskInstance.getDueDate());
  }


  public TaskInstance toModel(TaskInstanceEntity taskInstanceEntity) {
    return toModel(taskInstanceEntity, false);
  }


  public TaskInstance toModel(TaskInstanceEntity taskInstanceEntity, boolean withEvents) {
    var processInstance = taskInstanceEntity.getProcessInstanceId();
    return TaskInstance.builder()
        .id(Identifier.create(taskInstanceEntity.getId()))
        .taskKey(Code.create(taskInstanceEntity.getTaskKey()))
        .formKey(taskInstanceEntity.getFormKey()!=null ? Code.create(taskInstanceEntity.getFormKey()) : null)
        .name(Name.create(taskInstanceEntity.getName()))
        .externalId(Code.create(taskInstanceEntity.getExternalId()))
        .processInstanceId(Identifier.create(processInstance.getId()))
        .processNumber(ProcessNumber.create(processInstance.getNumber()))
        .engineProcessNumber(processInstance.getEngineProcessNumber())
        .businessKey(processInstance.getBusinessKey()!=null ? Code.create(processInstance.getBusinessKey()) : null)
        .processName(Code.create(processInstance.getName()))
        .processKey(Code.create(processInstance.getProcReleaseKey()))
        .applicationBase(Code.create(processInstance.getApplicationBase()))
        .status(taskInstanceEntity.getStatus())
        .priority(taskInstanceEntity.getPriority())
        .startedAt(taskInstanceEntity.getStartedAt())
        .startedBy(Code.create(taskInstanceEntity.getStartedBy()))
        .assignedAt(taskInstanceEntity.getAssignedAt())
        .assignedBy(taskInstanceEntity.getAssignedBy()!=null ? Code.create(taskInstanceEntity.getAssignedBy()) : null)
        .endedAt(taskInstanceEntity.getEndedAt())
        .endedBy(taskInstanceEntity.getEndedBy()!=null ? Code.create(taskInstanceEntity.getEndedBy()) : null)
        .candidateGroups(taskInstanceEntity.getCandidateGroups()!=null ? new HashSet<>(taskInstanceEntity.getCandidateGroups()) : null)
        .candidateUsers(taskInstanceEntity.getCandidateUsers()!=null ? new HashSet<>(taskInstanceEntity.getCandidateUsers()) : null)
        .taskInstanceEvents(withEvents ? eventMapper.toEventModelList(taskInstanceEntity.getTaskinstanceevents()) : null)
        .forms(taskInstanceEntity.getForms())
        .variables(taskInstanceEntity.getVariables())
        .dueDate(taskInstanceEntity.getDueDate())
        .build();
  }

  public TaskInstanceListPageDTO toTaskInstanceListPageDTO(PageableLista<TaskInstance> taskInstances) {
    var listDto = new TaskInstanceListPageDTO();
    listDto.setTotalElements(taskInstances.getTotalElements());
    listDto.setTotalPages(taskInstances.getTotalPages());
    listDto.setPageNumber(taskInstances.getPageNumber());
    listDto.setPageSize(taskInstances.getPageSize());
    listDto.setFirst(taskInstances.isFirst());
    listDto.setLast(taskInstances.isLast());
    listDto.setContent(taskInstances.getContent().stream().map(this::toTaskInstanceListDTO).toList());
    return listDto;
  }


  public TaskInstanceListDTO toTaskInstanceListDTO(TaskInstance model) {
    var dto = new TaskInstanceListDTO();
    dto.setId(model.getId().getValue());
    dto.setTaskKey(model.getTaskKey().getValue());
    dto.setFormKey(model.getFormKey()!=null ? model.getFormKey().getValue() : null);
    dto.setName(model.getName().getValue());
    dto.setCandidateGroups(String.join(",", model.getCandidateGroups()));
    dto.setCandidateUsers(String.join(",", model.getCandidateUsers()));
    dto.setProcessNumber(model.getProcessNumber().getValue());
    dto.setProcessInstanceId(model.getProcessInstanceId().getValue().toString());
    dto.setBusinessKey(model.getBusinessKey()!=null ? model.getBusinessKey().getValue() : null);
    dto.setProcessName(model.getProcessName()!=null ? model.getProcessName().getValue() : null);
    dto.setProcessKey(model.getProcessKey()!=null ? model.getProcessKey().getValue() : null);
    dto.setApplicationBase(model.getApplicationBase().getValue());
    dto.setStartedAt(model.getStartedAt());
    dto.setPriority(model.getPriority());
    dto.setAssignedBy(model.getAssignedBy()!=null ? model.getAssignedBy().getValue() : null);
    dto.setAssignedAt(model.getAssignedAt());
    dto.setEndedBy(model.getEndedBy()!=null ? model.getEndedBy().getValue() : null);
    dto.setEndedAt(model.getEndedAt());
    dto.setStatus(model.getStatus());
    dto.setStatusDesc(model.getStatus().getDescription());
    dto.setStartedBy(model.getStartedBy().getValue());
    dto.setVariables(toProcessVariableDTO(model.getVariables()));
    dto.setForms(toProcessVariableDTO(model.getForms()));
    dto.setProcessVariables(toProcessVariableDTO(model.getProcessVariables()));
    dto.setDueDate(model.getDueDate());
    dto.setUserProfileStartedBy(
        userProfileMapper.toDTO(model.getUserProfileStartedBy())
    );
    dto.setUserProfileEndedBy(
        userProfileMapper.toDTO(model.getUserProfileEndedBy())
    );
    dto.setUserProfileAssignedBy(
        userProfileMapper.toDTO(model.getUserProfileAssignedBy())
    );
    return dto;
  }


  public TaskInstanceDTO toTaskInstanceDTO(TaskInstance taskInstance) {
    var dto = new TaskInstanceDTO();
    dto.setTaskKey(taskInstance.getTaskKey().getValue());
    dto.setId(taskInstance.getId().getValue());
    dto.setFormKey(taskInstance.getFormKey()!=null ? taskInstance.getFormKey().getValue() : null);
    dto.setName(taskInstance.getName().getValue());
    dto.setExternalId(taskInstance.getExternalId().getValue());
    dto.setCandidateGroups(!taskInstance.getCandidateGroups().isEmpty() ? String.join(",", taskInstance.getCandidateGroups()) : null);
    dto.setCandidateUsers(!taskInstance.getCandidateUsers().isEmpty() ? String.join(",", taskInstance.getCandidateUsers()) : null);
    dto.setProcessInstanceId(taskInstance.getProcessInstanceId().getValue());
    dto.setProcessNumber(taskInstance.getProcessNumber().getValue());
    dto.setProcessKey(taskInstance.getProcessKey() != null ? taskInstance.getProcessKey().getValue() : null);
    dto.setBusinessKey(taskInstance.getBusinessKey()!=null ? taskInstance.getBusinessKey().getValue() : null);
    dto.setProcessName(taskInstance.getProcessName() != null ? taskInstance.getProcessName().getValue() : null);
    dto.setApplicationBase(taskInstance.getApplicationBase().getValue());
    dto.setStatus(taskInstance.getStatus());
    dto.setStatusDesc(taskInstance.getStatus().getDescription());
    dto.setSearchTerms(taskInstance.getSearchTerms());
    dto.setPriority(taskInstance.getPriority());
    dto.setStartedAt(taskInstance.getStartedAt());
    dto.setStartedBy(taskInstance.getStartedBy().getValue());
    dto.setAssignedBy(taskInstance.getAssignedBy()!=null?taskInstance.getAssignedBy().getValue():null);
    dto.setAssignedAt(taskInstance.getAssignedAt());
    dto.setEndedBy(taskInstance.getEndedBy()!=null?taskInstance.getEndedBy().getValue():null);
    dto.setEndedAt(taskInstance.getEndedAt());
    dto.setTaskInstanceEvents(eventMapper.toEventListDTO(taskInstance.getTaskInstanceEvents()));
    dto.setVariables(toProcessVariableDTO(taskInstance.getVariables()));
    dto.setForms(toProcessVariableDTO(taskInstance.getForms()));
    dto.setProcessVariables(toProcessVariableDTO(taskInstance.getProcessVariables()));
    dto.setDueDate(taskInstance.getDueDate());
    dto.setUserProfileStartedBy(
        userProfileMapper.toDTO(taskInstance.getUserProfileStartedBy())
    );
    dto.setUserProfileEndedBy(
        userProfileMapper.toDTO(taskInstance.getUserProfileEndedBy())
    );
    dto.setUserProfileAssignedBy(
        userProfileMapper.toDTO(taskInstance.getUserProfileAssignedBy())
    );
    return dto;
  }

  public TaskInstanceFilter toFilter(GetAllMyTasksCommand command) {
    List<VariablesExpression> vars = toVariablesExpressionList(command.getVariablesfilterdto());
    return TaskInstanceFilter.builder()
        .processInstanceId(command.getProcessInstanceId() != null ? Identifier.create(command.getProcessInstanceId()) : null)
        .processNumber(command.getProcessNumber() != null ? Code.create(command.getProcessNumber()) : null)
        .applicationBase((command.getApplicationBase() != null && !command.getApplicationBase().isBlank()) ? Code.create(command.getApplicationBase().trim()) : null)
        .processName((command.getProcessName() != null && !command.getProcessName().isBlank()) ? Name.create(command.getProcessName().trim()) : null)
        .status(command.getStatus() != null ? TaskInstanceStatus.valueOf(command.getStatus()) : null)
        .dateFrom(DateUtil.stringToLocalDate.apply(command.getDateFrom()))
        .dateTo(DateUtil.stringToLocalDate.apply(command.getDateTo()))
        .variablesExpressions(vars)
        .page(command.getPage())
        .size(command.getSize())
        .name(command.getName() != null && !command.getName().isBlank() ? Name.create(command.getName()) : null)
        .processReleaseKey(command.getProcessReleaseKey() != null && !command.getProcessReleaseKey().isBlank() ? Code.create(command.getProcessReleaseKey()) : null)
        .filterByCurrentUser(true)
        .build();
  }


  public List<TaskVariableDTO> toTaskVariableListDTO(Map<String,Object> variables) {
    return variables==null ? List.of() : variables.entrySet().stream()
        .map(e-> new TaskVariableDTO(e.getKey(),e.getValue()))
        .toList();
  }

  public TaskVariablesFormsDTO toTaskVariablesFormsDTO(Map<String, Object> variables) {
    TaskVariablesFormsDTO dto = new TaskVariablesFormsDTO();
    Object formsObj = variables.get(VariableTag.FORMS.getCode());
    if (formsObj instanceof Map<?, ?> formsMap) {
      dto.setForms(
          formsMap.entrySet().stream()
              .map(e -> new TaskVariableDTO(e.getKey().toString(), e.getValue()))
              .toList()
      );
    }
    Object varsObj = variables.get(VariableTag.VARIABLES.getCode());
    if (varsObj instanceof Map<?, ?> varsMap) {
      dto.setVariables(
          varsMap.entrySet().stream()
              .map(e -> new TaskVariableDTO(e.getKey().toString(), e.getValue()))
              .toList()
      );
    }

    return dto;
  }




  public List<ProcessVariableDTO> toProcessVariableDTO(Map<String,Object> variables){
    return variables==null ? List.of() : variables.entrySet().stream()
        .map(e-> new ProcessVariableDTO(e.getKey(),e.getValue()))
        .toList();
  }


  public TaskInstanceStatsDTO toTaskInstanceStatsDto(TaskStatistics taskStatistics) {
    return new TaskInstanceStatsDTO(
        taskStatistics.getTotalTaskInstances(),
        taskStatistics.getTotalAvailableTasks(),
        taskStatistics.getTotalAssignedTasks(),
        taskStatistics.getTotalSuspendedTasks(),
        taskStatistics.getTotalCompletedTasks(),
        taskStatistics.getTotalCanceledTasks()
    );
  }

  public TaskInstanceFilter toFilter(ListTaskInstancesCommand command) {
    List<VariablesExpression> vars = toVariablesExpressionList(command.getVariablesfilterdto());
    return TaskInstanceFilter.builder()
        .processInstanceId(command.getProcessInstanceId() != null ? Identifier.create(command.getProcessInstanceId()) : null)
        .processNumber(command.getProcessNumber() != null ? Code.create(command.getProcessNumber()) : null)
        .applicationBase((command.getApplicationBase() != null && !command.getApplicationBase().isBlank()) ? Code.create(command.getApplicationBase().trim()) : null)
        .processName((command.getProcessName() != null && !command.getProcessName().isBlank()) ? Name.create(command.getProcessName().trim()) : null)
        .candidateGroups(splitCommaSeparated(command.getCandidateGroups()))
        .candidateUsers(splitCommaSeparated(command.getCandidateUsers()))
        .user(command.getUser() != null ? Code.create(command.getUser()) : null)
        .status(command.getStatus() != null ? TaskInstanceStatus.valueOf(command.getStatus()) : null)
        .dateFrom(DateUtil.stringToLocalDate.apply(command.getDateFrom()))
        .dateTo(DateUtil.stringToLocalDate.apply(command.getDateTo()))
        .variablesExpressions(vars)
        .page(command.getPage())
        .size(command.getSize())
        .name(command.getName() != null && !command.getName().isBlank() ? Name.create(command.getName()) : null)
        .processReleaseKey(command.getProcessReleaseKey() != null && !command.getProcessReleaseKey().isBlank() ? Code.create(command.getProcessReleaseKey()) : null)
        .filterByCurrentUser(command.isFilterByCurrentUser())
        .build();
  }

  public List<VariablesExpression> toVariablesExpressionList(VariablesFilterDTO dto){
    return dto.getVariables().stream()
        .map(variablesExpressionDTO -> VariablesExpression.builder()
            .name(variablesExpressionDTO.getName())
            .operator(variablesExpressionDTO.getOperator())
            .value(variablesExpressionDTO.getValue())
            .build()
        ).toList();
  }

  private Set<String> splitCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return new HashSet<>();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
  }

  private void syncCandidateGroups(TaskInstanceEntity entity, Set<String> candidateGroups) {
    if (entity.getCandidateGroups() == null) {
      entity.setCandidateGroups(new HashSet<>());
    } else {
      entity.getCandidateGroups().clear();
    }

    if (candidateGroups != null) {
      candidateGroups.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(group -> !group.isBlank())
          .forEach(entity.getCandidateGroups()::add);
    }
  }

  private void syncCandidateUsers(TaskInstanceEntity entity, Set<String> candidateUsers) {
    if (entity.getCandidateUsers() == null) {
      entity.setCandidateUsers(new HashSet<>());
    } else {
      entity.getCandidateUsers().clear();
    }

    if (candidateUsers != null) {
      candidateUsers.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(user -> !user.isBlank())
          .forEach(entity.getCandidateUsers()::add);
    }
  }

}
