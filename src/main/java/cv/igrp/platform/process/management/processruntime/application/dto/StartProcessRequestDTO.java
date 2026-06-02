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
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class StartProcessRequestDTO  {


  private String processDefinitionId ;

  @NotBlank(message = "The field <processKey> is required")
  private String processKey ;


  private String businessKey ;
  @NotBlank(message = "The field <applicationBase> is required")

  private String applicationBase ;


  private Integer priority ;

  @Valid
  private List<ProcessVariableDTO> variables = new ArrayList<>();

  @Valid
  private List<ProcessTaskAssignmentRuleDTO> assignmentRules = new ArrayList<>();

}
