package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListTaskAssignmentRulesCommand implements Command {

  private String processInstanceId;
  private String processDefinitionKey;
  private String taskDefinitionKey;
  private String assignee;
  private String candidateUsers;
  private TaskAssignmentMode assignmentMode;
  private Boolean consumed;
  private Boolean active;
  private String createdByTask;
  private Integer page;
  private Integer size;
}
