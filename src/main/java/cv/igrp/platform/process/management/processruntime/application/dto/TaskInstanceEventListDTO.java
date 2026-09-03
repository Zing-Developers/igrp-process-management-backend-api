/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
// Audit trio (time, raw principal, enriched profile) follows the platform pattern. NOTE for iGRP
// Studio model owners: mirror these six fields in the generator model so a regeneration keeps them.
public class TaskInstanceEventListDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private java.time.LocalDateTime createdAt ;

  private java.time.LocalDateTime updatedAt ;

  private String createdBy ;

  private String updatedBy ;

  private UserProfileDTO userProfileCreatedBy ;

  private UserProfileDTO userProfileUpdatedBy ;

  
  
  private UUID id ;
  
  
  private UUID taskInstanceId ;
  
  
  private String eventType ;
  
  
  private LocalDateTime performedAt ;
  
  
  private String performedBy ;
  
  
  private String obs ;
  
  
  private TaskInstanceStatus status ;
  
  @Valid
  private UserProfileDTO userProfilePerformedBy ;

}