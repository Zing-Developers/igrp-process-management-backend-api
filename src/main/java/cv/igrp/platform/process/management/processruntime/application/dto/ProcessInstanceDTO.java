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
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
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
public class ProcessInstanceDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private java.time.LocalDateTime createdAt ;

  private java.time.LocalDateTime updatedAt ;

  private String createdBy ;

  private String updatedBy ;

  private UserProfileDTO userProfileCreatedBy ;

  private UserProfileDTO userProfileUpdatedBy ;

  
  
  private UUID id ;
  
  
  private String procReleaseKey ;
  
  
  private String procReleaseId ;
  
  
  private String number ;
  
  
  private ProcessInstanceStatus status ;
  
  
  private String statusDesc ;
  
  
  private String businessKey ;
  
  
  private String version ;
  
  
  private LocalDateTime startedAt ;
  
  
  private String startedBy ;
  
  
  private LocalDateTime endedAt ;
  
  
  private String endedBy ;
  
  
  private LocalDateTime canceledAt ;
  
  
  private String cancelledBy ;
  
  
  private String obsCancel ;
  
  
  private String applicationBase ;
  
  
  private String name ;
  
  
  private String progress ;
  
  
  private Integer priority ;
  
  @Valid
  private List<ProcessVariableDTO> variables = new ArrayList<>();
  
  @Valid
  private UserProfileDTO userProfileStartedBy ;
  
  @Valid
  private UserProfileDTO userProfileEndedBy ;
  
  @Valid
  private UserProfileDTO userProfileCancelledBy ;

}