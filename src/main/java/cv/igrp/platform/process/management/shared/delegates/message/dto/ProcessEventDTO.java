package cv.igrp.platform.process.management.shared.delegates.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessEventDTO {

  private String taskId; // optional
  private String messageName; // optional
  private String businessKey;
  private Map<String, Object> variables; // optional
  private List<TaskAssignmentRuleDTO> assignmentRules = new ArrayList<>();

}
