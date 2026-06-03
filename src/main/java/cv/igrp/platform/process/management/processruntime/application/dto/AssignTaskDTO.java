/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.processruntime.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class AssignTaskDTO  {

  @Size(min = 0, message = "The field length <user> must be at least 0 characters")
  
  private String user ;
  
  
  private Integer priority ;
  
  
  private String note ;
  
  
  private String candidateGroups ;


  private String candidateUsers ;

}
