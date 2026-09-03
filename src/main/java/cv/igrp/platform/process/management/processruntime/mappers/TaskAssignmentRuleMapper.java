package cv.igrp.platform.process.management.processruntime.mappers;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.processruntime.application.commands.ListTaskAssignmentRulesCommand;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleListPageDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleUpdateDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TaskAssignmentRuleMapper {

  public TaskAssignmentRuleFilter toFilter(ListTaskAssignmentRulesCommand command) {
    return TaskAssignmentRuleFilter.builder()
        .processInstanceId(toIdentifier(command.getProcessInstanceId()))
        .processDefinitionKey(toCode(command.getProcessDefinitionKey()))
        .taskDefinitionKey(toCode(command.getTaskDefinitionKey()))
        .assignee(toCode(command.getAssignee()))
        .candidateUsers(splitCommaSeparated(command.getCandidateUsers()))
        .candidateGroups(splitCommaSeparated(command.getCandidateGroups()))
        .assignmentMode(command.getAssignmentMode())
        .consumed(command.getConsumed())
        .active(command.getActive())
        .createdByTask(toIdentifier(command.getCreatedByTask()))
        .page(command.getPage())
        .size(command.getSize())
        .build();
  }

  public TaskAssignmentRuleListPageDTO toListPageDTO(PageableLista<TaskAssignmentRule> rules) {
    var dto = new TaskAssignmentRuleListPageDTO();
    dto.setTotalElements(rules.getTotalElements());
    dto.setTotalPages(rules.getTotalPages());
    dto.setPageNumber(rules.getPageNumber());
    dto.setPageSize(rules.getPageSize());
    dto.setFirst(rules.isFirst());
    dto.setLast(rules.isLast());
    dto.setContent(rules.getContent().stream().map(this::toListDTO).toList());
    return dto;
  }

  public TaskAssignmentRuleListDTO toListDTO(TaskAssignmentRule rule) {
    var dto = new TaskAssignmentRuleListDTO();
    dto.setId(rule.getId().getValue());
    dto.setProcessDefinitionKey(rule.getProcessDefinitionKey().getValue());
    dto.setProcessInstanceId(rule.getProcessInstanceId() != null ? rule.getProcessInstanceId().getValue() : null);
    dto.setTaskDefinitionKey(rule.getTaskDefinitionKey().getValue());
    dto.setAssignee(rule.getAssignee() != null ? rule.getAssignee().getValue() : null);
    dto.setCandidateUsers(String.join(",", rule.getCandidateUsers()));
    dto.setCandidateGroups(String.join(",", rule.getCandidateGroups()));
    dto.setAssignmentMode(rule.getAssignmentMode());
    dto.setPriority(rule.getPriority());
    dto.setConsumed(rule.isConsumed());
    dto.setActive(rule.isActive());
    dto.setCreatedByTask(rule.getCreatedByTask() != null ? rule.getCreatedByTask().getValue() : null);
    AuditMapping.apply(dto, rule.getAudit());
    return dto;
  }

  public Code toAssignee(TaskAssignmentRuleUpdateDTO dto) {
    if (dto == null || dto.getAssignee() == null || dto.getAssignee().isBlank()) {
      return null;
    }
    return Code.create(dto.getAssignee().trim());
  }

  public Set<String> toCandidateUsers(TaskAssignmentRuleUpdateDTO dto) {
    if (dto == null) {
      return Set.of();
    }
    return splitCommaSeparated(dto.getCandidateUsers());
  }

  public Set<String> toCandidateGroups(TaskAssignmentRuleUpdateDTO dto) {
    if (dto == null) {
      return Set.of();
    }
    return splitCommaSeparated(dto.getCandidateGroups());
  }

  private Identifier toIdentifier(String value) {
    return value != null && !value.isBlank() ? Identifier.create(value.trim()) : null;
  }

  private Code toCode(String value) {
    return value != null && !value.isBlank() ? Code.create(value.trim()) : null;
  }

  private Set<String> splitCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return new HashSet<>();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .collect(Collectors.toCollection(HashSet::new));
  }
}
