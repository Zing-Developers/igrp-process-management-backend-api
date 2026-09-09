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
// Audit trio (time, raw principal, enriched profile) follows the platform pattern.
public class TaskAssignmentRuleListDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private java.time.LocalDateTime createdAt;

  private java.time.LocalDateTime updatedAt;

  private String createdBy;

  private String updatedBy;

  private UserProfileDTO userProfileCreatedBy;

  private UserProfileDTO userProfileUpdatedBy;

  private UUID id;
  private String processDefinitionKey;
  private UUID processInstanceId;
  private String taskDefinitionKey;
  private String assignee;
  private String candidateUsers;
  private String candidateGroups;
  private TaskAssignmentMode assignmentMode;
  private Integer priority;
  private boolean consumed;
  private boolean active;
  private UUID createdByTask;
}
