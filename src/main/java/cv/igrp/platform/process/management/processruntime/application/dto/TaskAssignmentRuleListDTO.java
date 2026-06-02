package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class TaskAssignmentRuleListDTO {

  private UUID id;
  private String processDefinitionKey;
  private UUID processInstanceId;
  private String taskDefinitionKey;
  private String assignee;
  private String candidateUsers;
  private TaskAssignmentMode assignmentMode;
  private Integer priority;
  private boolean consumed;
  private boolean active;
  private UUID createdByTask;
}
