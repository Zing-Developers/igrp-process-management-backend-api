package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import lombok.Builder;
import lombok.Getter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
public class TaskAssignmentRule {

  private final Identifier id;
  private final Code processDefinitionKey;
  private final Identifier processInstanceId;
  private final Code taskDefinitionKey;
  private final Code assignee;
  private final Set<String> candidateUsers;
  private final TaskAssignmentMode assignmentMode;
  private final Integer priority;
  private final boolean consumed;
  private final boolean active;
  private final Identifier createdByTask;
  private final boolean persisted;

  @Builder
  public TaskAssignmentRule(
      Identifier id,
      Code processDefinitionKey,
      Identifier processInstanceId,
      Code taskDefinitionKey,
      Code assignee,
      Collection<String> candidateUsers,
      TaskAssignmentMode assignmentMode,
      Integer priority,
      Boolean consumed,
      Boolean active,
      Identifier createdByTask,
      Boolean persisted
  ) {
    this.id = id == null ? Identifier.generate() : id;
    this.processDefinitionKey = Objects.requireNonNull(processDefinitionKey, "Process definition key cannot be null!");
    this.processInstanceId = processInstanceId;
    this.taskDefinitionKey = Objects.requireNonNull(taskDefinitionKey, "Task definition key cannot be null!");
    this.assignee = assignee;
    this.candidateUsers = normalizeCandidateUsers(candidateUsers);
    this.assignmentMode = assignmentMode == null ? TaskAssignmentMode.ONE_TIME : assignmentMode;
    this.priority = priority;
    this.consumed = consumed != null && consumed;
    this.active = active == null || active;
    this.createdByTask = createdByTask;
    this.persisted = persisted != null && persisted;
  }

  private Set<String> normalizeCandidateUsers(Collection<String> candidateUsers) {
    if (candidateUsers == null || candidateUsers.isEmpty()) {
      return Set.of();
    }
    Set<String> users = new LinkedHashSet<>();
    candidateUsers.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(user -> !user.isBlank())
        .forEach(users::add);
    return users;
  }

  public boolean hasAssignee() {
    return assignee != null;
  }

  public boolean hasCandidateUsers() {
    return !candidateUsers.isEmpty();
  }
}
