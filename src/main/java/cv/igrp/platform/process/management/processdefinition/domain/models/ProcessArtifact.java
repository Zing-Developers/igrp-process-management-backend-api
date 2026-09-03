package cv.igrp.platform.process.management.processdefinition.domain.models;

import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

@Getter
public class ProcessArtifact {

  private cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit;

  public void setAudit(cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit) {
    this.audit = audit;
  }

  public static final String DEFAULT_VALUE = "NOT_SET";

  private final Identifier id;
  private final Name name;
  private final Code key;
  private String formKey;
  private final Code processDefinitionId;
  private Set<String> candidateGroups;
  private String dueDate;
  private Integer priority;

  @Builder
  public ProcessArtifact(Identifier id,
                         Name name,
                         Code key,
                         String formKey,
                         Code processDefinitionId,
                         Set<String> candidateGroups,
                         String dueDate,
                         Integer priority
  ) {
    this.id = id ==  null ? Identifier.generate() : id;
    this.name = Objects.requireNonNull(name, "The Name of the task cannot be null!");
    this.key = Objects.requireNonNull(key, "Task Key Id cannot be null!");
    this.formKey = formKey == null || formKey.isBlank() ? DEFAULT_VALUE : formKey;
    this.processDefinitionId = Objects.requireNonNull(processDefinitionId, "ProcessDefinition Id cannot be null!");
    this.candidateGroups = candidateGroups == null ? new HashSet<>() : candidateGroups;
    this.dueDate = dueDate;
    this.priority = priority;
  }

  public void update(ProcessArtifact processArtifact) {
    this.formKey = processArtifact.formKey != null ? processArtifact.formKey : this.formKey;
    this.candidateGroups = !processArtifact.candidateGroups.isEmpty() ? processArtifact.candidateGroups : this.candidateGroups;
    this.priority = processArtifact.priority != null ? processArtifact.priority : this.priority;
    this.dueDate = processArtifact.dueDate != null ? processArtifact.dueDate : this.dueDate;
  }

  public boolean isFormKeySet() {
    return !DEFAULT_VALUE.equals(this.formKey);
  }

}
