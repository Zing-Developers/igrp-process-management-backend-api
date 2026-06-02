package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskAssignmentRuleUpdateDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskAssignmentRuleCommand implements Command {

  private TaskAssignmentRuleUpdateDTO taskAssignmentRuleUpdateDTO;

  @NotBlank(message = "The field <id> is required")
  private String id;
}
