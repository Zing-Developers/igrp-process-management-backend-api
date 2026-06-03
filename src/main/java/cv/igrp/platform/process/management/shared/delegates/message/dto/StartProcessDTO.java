package cv.igrp.platform.process.management.shared.delegates.message.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartProcessDTO {

  private String processDefinitionId ;
  private String processKey ;
  private String businessKey ;
  private String applicationBase ;
  private Integer priority ;

  private List<ProcessVariableDTO> variables = new ArrayList<>();
  private List<TaskAssignmentRuleDTO> assignmentRules = new ArrayList<>();

}
