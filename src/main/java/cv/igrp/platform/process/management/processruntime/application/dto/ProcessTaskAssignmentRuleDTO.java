package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class ProcessTaskAssignmentRuleDTO {

  @NotBlank(message = "The field <taskKey> is required")
  private String taskKey;

  private String assignee;

  private String candidateUsers;

  private String candidateGroups;

  private TaskAssignmentMode assignmentMode;

  private Integer priority;
}
