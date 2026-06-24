package cv.igrp.platform.process.management.shared.delegates.message.dto;

import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignmentRuleDTO {

  private String taskKey;
  private String assignee;
  private String candidateUsers;
  private String candidateGroups;
  private TaskAssignmentMode assignmentMode;
  private Integer priority;
}
