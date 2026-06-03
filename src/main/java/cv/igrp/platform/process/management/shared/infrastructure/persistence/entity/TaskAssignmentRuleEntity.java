package cv.igrp.platform.process.management.shared.infrastructure.persistence.entity;

import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.config.AuditEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "t_task_assignment_rule",
    indexes = {
        @Index(
            name = "idx_task_assignment_rule_visibility",
            columnList = "process_instance_id, task_definition_key, active, consumed"
        ),
        @Index(
            name = "idx_task_assignment_rule_created_by_task",
            columnList = "created_by_task"
        ),
        @Index(
            name = "idx_task_assignment_rule_definition",
            columnList = "process_definition_key, task_definition_key, active, consumed"
        ),
        @Index(
            name = "idx_task_assignment_rule_assignee",
            columnList = "assignee, active, consumed"
        )
    }
)
public class TaskAssignmentRuleEntity extends AuditEntity {

  @Id
  @Column(name = "id", unique = true, nullable = false)
  private UUID id;

  @NotBlank(message = "processDefinitionKey is mandatory")
  @Column(name = "process_definition_key", nullable = false)
  private String processDefinitionKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "process_instance_id", referencedColumnName = "id")
  private ProcessInstanceEntity processInstanceId;

  @NotBlank(message = "taskDefinitionKey is mandatory")
  @Column(name = "task_definition_key", nullable = false)
  private String taskDefinitionKey;

  @Column(name = "assignee")
  private String assignee;

  @NotAudited
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "t_task_assignment_rule_candidate_user",
      joinColumns = @JoinColumn(name = "assignment_rule_id")
  )
  @Column(name = "user_id", nullable = false)
  private Set<String> candidateUsers = new HashSet<>();

  @NotNull(message = "assignmentMode is mandatory")
  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_mode", nullable = false)
  private TaskAssignmentMode assignmentMode;

  @Column(name = "priority")
  private Integer priority;

  @Column(name = "consumed", nullable = false)
  private Boolean consumed;

  @Column(name = "active", nullable = false)
  private Boolean active;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_task", referencedColumnName = "id")
  private TaskInstanceEntity createdByTask;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (assignmentMode == null) {
      assignmentMode = TaskAssignmentMode.ONE_TIME;
    }
    if (consumed == null) {
      consumed = false;
    }
    if (active == null) {
      active = true;
    }
  }
}
