package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@ToString
public class TaskOperationData {
  private final Identifier id;
  private final Code currentUser;
  private final Code targetUser;
  private final Integer priority;
  private final String note;
  private final Map<String,Object> variables;
  private final Map<String,Object> forms;
  private final List<String> candidateGroups;
  private final List<String> candidateUsers;

  @Builder
  public TaskOperationData(String id,
                           Code currentUser,
                           Integer priority,
                           String targetUser,
                           String note,
                           Map<String,Object> variables,
                           Map<String,Object> forms,
                           List<String> candidateGroups,
                           List<String> candidateUsers) {
    this.id = id != null && !id.isBlank() ? Identifier.create(id) : Identifier.generate();
    this.currentUser = Objects.requireNonNull(currentUser,"Current User can't be null!");
    this.targetUser = targetUser != null && !targetUser.isBlank() ? Code.create(targetUser) : null;
    this.priority = (priority!=null && priority>0) ? priority: null;
    this.note = note != null && !note.trim().isBlank() ? note : null;
    this.variables = (variables!=null) ? variables : Map.of();
    this.forms = (forms!=null) ? forms : Map.of();
    this.candidateGroups = candidateGroups == null ? new ArrayList<>() : candidateGroups;
    this.candidateUsers = candidateUsers == null ? new ArrayList<>() : candidateUsers;
  }

  public void validateVariablesAndForms() {
    variables.forEach((k,v)->{
      if(k==null || k.isBlank() || v==null)
        throw IgrpResponseStatusException.badRequest("Invalid param for variable [name:"+ k +"] [value:"+ v +"]");
    });
    forms.forEach((k,v)->{
      if(k==null || k.isBlank() || v==null)
        throw IgrpResponseStatusException.badRequest("Invalid param for form [name:"+ k +"] [value:"+ v +"]");
    });
  }

}
