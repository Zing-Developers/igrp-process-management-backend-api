/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.area.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.process.management.area.application.dto.ProcessDefinitionDTO;
import cv.igrp.platform.process.management.shared.application.constants.Status;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
// Audit trio (time, raw principal, enriched profile) follows the platform pattern. NOTE for iGRP
// Studio model owners: mirror the userProfile* fields in the generator model so a regeneration keeps them.
public class AreaDTO implements cv.igrp.platform.process.management.shared.security.AuditedResponse {

  private cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO userProfileCreatedBy ;

  private cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO userProfileUpdatedBy ;

  @NotNull(message = "The field <id> is required")
  
  private UUID id ;
  @NotBlank(message = "The field <code> is required")
  
  private String code ;
  @NotBlank(message = "The field <name> is required")
  
  private String name ;
  @NotBlank(message = "The field <applicationBase> is required")
  
  private String applicationBase ;
  @NotNull(message = "The field <areaId> is required")
  
  private UUID areaId ;
  @NotNull(message = "The field <status> is required")
  
  private Status status ;
  
  
  private String statusDesc ;
  
  @Valid
  private List<ProcessDefinitionDTO> process = new ArrayList<>();
  
  
  private LocalDateTime createdAt ;
  
  
  private LocalDateTime updatedAt ;
  
  
  private String createdBy ;
  
  
  private String updatedBy ;
  
  
  private String description ;
  
  
  private String color ;

}