/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processdefinition.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
// Audit trio (time, raw principal, enriched profile) follows the platform pattern. NOTE for iGRP
// Studio model owners: mirror these six fields in the generator model so a regeneration keeps them.
public class ProcessSequenceDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private java.time.LocalDateTime createdAt ;

  private java.time.LocalDateTime updatedAt ;

  private String createdBy ;

  private String updatedBy ;

  private cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO userProfileCreatedBy ;

  private cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO userProfileUpdatedBy ;

  
  
  private UUID id ;
  
  
  private String name ;
  
  
  private String prefix ;
  
  
  private short checkDigitSize ;
  
  
  private short padding ;
  
  
  private String dateFormat ;
  
  
  private Long nextNumber ;
  
  
  private short numberIncrement ;
  
  
  private String processDefinitionKey ;
  
  
  private String separator ;

}