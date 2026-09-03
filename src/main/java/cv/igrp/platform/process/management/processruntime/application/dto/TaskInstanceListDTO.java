/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.process.management.processruntime.application.dto.ProcessVariableDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
// Audit trio (time, raw principal, enriched profile) follows the platform pattern. NOTE for iGRP
// Studio model owners: mirror these six fields in the generator model so a regeneration keeps them.
public class TaskInstanceListDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private java.time.LocalDateTime createdAt ;

  private java.time.LocalDateTime updatedAt ;

  private String createdBy ;

  private String updatedBy ;

  private UserProfileDTO userProfileCreatedBy ;

  private UserProfileDTO userProfileUpdatedBy ;

  
  
  private UUID id ;
  
  
  private String taskKey ;
  
  
  private String formKey ;
  
  
  private String name ;
  
  
  private String candidateGroups ;


  private String candidateUsers ;
  
  
  private String processInstanceId ;
  
  
  private String processNumber ;
  
  
  private String processName ;
  
  
  private String processKey ;
  
  
  private String businessKey ;
  
  
  private Integer priority ;
  
  
  private String assignedBy ;
  
  
  private LocalDateTime assignedAt ;
  
  
  private String startedBy ;
  
  
  private LocalDateTime startedAt ;
  
  
  private String endedBy ;
  
  
  private LocalDateTime endedAt ;
  
  
  private TaskInstanceStatus status ;
  
  
  private String statusDesc ;
  
  @Valid
  private List<ProcessVariableDTO> variables = new ArrayList<>();
  
  
  private String applicationBase ;
  
  @Valid
  private List<ProcessVariableDTO> forms = new ArrayList<>();
  
  @Valid
  private List<ProcessVariableDTO> processVariables = new ArrayList<>();
  
  
  private LocalDateTime dueDate ;
  
  @Valid
  private UserProfileDTO userProfileEndedBy ;
  
  @Valid
  private UserProfileDTO userProfileAssignedBy ;
  
  @Valid
  private UserProfileDTO userProfileStartedBy ;

}
