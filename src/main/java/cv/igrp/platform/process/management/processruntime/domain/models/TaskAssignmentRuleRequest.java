package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class TaskAssignmentRuleRequest {

  private final Code taskKey;
  private final Code assignee;
  private final List<String> candidateUsers;
  private final TaskAssignmentMode assignmentMode;
  private final Integer priority;

  @Builder
  public TaskAssignmentRuleRequest(
      Code taskKey,
      Code assignee,
      List<String> candidateUsers,
      TaskAssignmentMode assignmentMode,
      Integer priority
  ) {
    this.taskKey = Objects.requireNonNull(taskKey, "Task key cannot be null!");
    this.assignee = assignee;
    this.candidateUsers = candidateUsers == null ? List.of() : candidateUsers;
    this.assignmentMode = assignmentMode == null ? TaskAssignmentMode.ONE_TIME : assignmentMode;
    this.priority = priority != null && priority > 0 ? priority : null;
  }

  public boolean matches(Code candidateTaskKey) {
    return taskKey.equals(candidateTaskKey);
  }

  public boolean hasAssignee() {
    return assignee != null;
  }

  public boolean hasCandidateUsers() {
    return !candidateUsers.isEmpty();
  }
}
