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
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceEventListDTO;
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
public class TaskInstanceDTO  {

  
  
  private UUID id ;
  
  
  private String taskKey ;
  
  
  private String formKey ;
  
  
  private String name ;
  
  
  private String externalId ;
  
  
  private String candidateGroups ;


  private String candidateUsers ;
  
  
  private UUID processInstanceId ;
  
  
  private String processKey ;
  
  
  private String processNumber ;
  
  
  private String businessKey ;
  
  
  private String processName ;
  
  
  private String applicationBase ;
  
  
  private LocalDateTime assignedAt ;
  
  
  private String assignedBy ;
  
  
  private String searchTerms ;
  
  
  private Integer priority ;
  
  
  private LocalDateTime startedAt ;
  
  
  private String startedBy ;
  
  
  private TaskInstanceStatus status ;
  
  
  private String statusDesc ;
  
  
  private LocalDateTime endedAt ;
  
  
  private String endedBy ;
  
  @Valid
  private List<TaskInstanceEventListDTO> taskInstanceEvents = new ArrayList<>();
  
  @Valid
  private List<ProcessVariableDTO> variables = new ArrayList<>();
  
  @Valid
  private List<ProcessVariableDTO> forms = new ArrayList<>();
  
  @Valid
  private List<ProcessVariableDTO> processVariables = new ArrayList<>();
  
  
  private LocalDateTime dueDate ;
  
  @Valid
  private UserProfileDTO userProfileAssignedBy ;
  
  @Valid
  private UserProfileDTO userProfileEndedBy ;
  
  @Valid
  private UserProfileDTO userProfileStartedBy ;

}
