package cv.igrp.platform.process.management.processdefinition.domain.models;

import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

@Getter
public class TaskPriority {

  private cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit;

  public void setAudit(cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit) {
    this.audit = audit;
  }

  private final Identifier id;
  private Code code;
  private String label;
  private String processDefinitionKey;
  private Integer weight;
  private String color;

  @Builder
  public TaskPriority(Identifier id,
                      Code code,
                      String label,
                      String processDefinitionKey,
                      Integer weight,
                      String color
  ) {
    this.id = id ==  null ? Identifier.generate() : id;
    this.code = Objects.requireNonNull(code, "Code cannot be null");
    this.label = Objects.requireNonNull(label, "Label cannot be null");
    this.processDefinitionKey = Objects.requireNonNull(processDefinitionKey, "Process Definition Key cannot be null");
    this.weight = Objects.requireNonNull(weight, "Weight cannot be null");
    this.color = color;
  }

  public void update(TaskPriority taskPriority) {
    this.code = taskPriority.code;
    this.label = taskPriority.label;
    this.weight = taskPriority.weight;
    this.color = taskPriority.color != null ? taskPriority.color : this.color;
  }

}
