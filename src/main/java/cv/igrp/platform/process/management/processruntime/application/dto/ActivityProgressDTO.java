/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskVariableDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class ActivityProgressDTO  {

  
  
  private String activityKey ;
  
  
  private String activityName ;
  
  
  private String status ;
  
  
  private String type ;
  
  
  private String processInstanceId ;
  
  
  private String assignee ;
  
  
  private String candidateUsers ;
  
  
  private String candidateGroups ;
  
  
  private LocalDateTime startTime ;
  
  
  private LocalDateTime endTime ;
  
  
  private Long durationMillis ;
  
  
  private String activityId ;
  
  @Valid
  private List<TaskVariableDTO> variables = new ArrayList<>();
  
  
  private String executionId ;
  
  
  private String taskId ;
  
  
  private String activityInstanceId ;
  
  
  private String treeNumber ;
  
  @Valid
  private List<TaskVariableDTO> forms = new ArrayList<>();
  
  @Valid
  private UserProfileDTO userProfileAssignee ;


  private String jobId ;


  private String errorMessage ;


  private Integer attempts ;


  private LocalDateTime firstFailedAt ;


  private LocalDateTime lastFailedAt ;

}