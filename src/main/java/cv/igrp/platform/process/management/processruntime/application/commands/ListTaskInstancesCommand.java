package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.process.management.processruntime.application.dto.VariablesFilterDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListTaskInstancesCommand implements Command {

  
  private VariablesFilterDTO variablesfilterdto;
  @NotBlank(message = "The field <processInstanceId> is required")
  private String processInstanceId;
  @NotBlank(message = "The field <processNumber> is required")
  private String processNumber;
  @NotBlank(message = "The field <processReleaseKey> is required")
  private String processReleaseKey;
  @NotBlank(message = "The field <applicationBase> is required")
  private String applicationBase;
  @NotBlank(message = "The field <candidateGroups> is required")
  private String candidateGroups;
  private String candidateUsers;
  @NotBlank(message = "The field <user> is required")
  private String user;
  @NotBlank(message = "The field <status> is required")
  private String status;
  @NotBlank(message = "The field <dateFrom> is required")
  private String dateFrom;
  @NotBlank(message = "The field <dateTo> is required")
  private String dateTo;
  @NotNull(message = "The field <page> is required")
  private Integer page;
  @NotNull(message = "The field <size> is required")
  private Integer size;
  @NotBlank(message = "The field <name> is required")
  private String name;
  @NotBlank(message = "The field <processName> is required")
  private String processName;
  @NotNull(message = "The field <filterByCurrentUser> is required")
  private boolean filterByCurrentUser;
  private Integer priority;

}
