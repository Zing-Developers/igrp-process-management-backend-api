package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import lombok.Builder;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class TaskAssignmentRuleFilter {

  private final Identifier processInstanceId;
  private final Code processDefinitionKey;
  private final Code taskDefinitionKey;
  private final Code assignee;
  private final Set<String> candidateUsers;
  private final Set<String> candidateGroups;
  private final TaskAssignmentMode assignmentMode;
  private final Boolean consumed;
  private final Boolean active;
  private final Identifier createdByTask;
  private final Integer page;
  private final Integer size;

  @Builder
  private TaskAssignmentRuleFilter(
      Identifier processInstanceId,
      Code processDefinitionKey,
      Code taskDefinitionKey,
      Code assignee,
      Set<String> candidateUsers,
      Set<String> candidateGroups,
      TaskAssignmentMode assignmentMode,
      Boolean consumed,
      Boolean active,
      Identifier createdByTask,
      Integer page,
      Integer size
  ) {
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.taskDefinitionKey = taskDefinitionKey;
    this.assignee = assignee;
    this.candidateUsers = candidateUsers == null ? new HashSet<>() : candidateUsers;
    this.candidateGroups = candidateGroups == null ? new HashSet<>() : candidateGroups;
    this.assignmentMode = assignmentMode;
    this.consumed = consumed;
    this.active = active;
    this.createdByTask = createdByTask;
    this.page = page == null ? 0 : page;
    this.size = size == null ? 50 : size;
  }
}
