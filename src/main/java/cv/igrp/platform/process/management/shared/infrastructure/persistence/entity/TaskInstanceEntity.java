/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.process.management.shared.infrastructure.persistence.entity;


import cv.igrp.platform.process.management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.*;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import org.hibernate.type.SqlTypes;


@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_task_instance")
public class TaskInstanceEntity extends AuditEntity {

  @Id
  @Column(name = "id", unique = true, nullable = false)
  private UUID id;


  @Column(name="task_key", length=50)
  private String taskKey;


  @Column(name="external_id")
  private String externalId;


  @Column(name="form_key")
  private String formKey;


  @Column(name="name", length=100)
  private String name;


  @NotAudited
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "t_task_instance_candidate_group",
      joinColumns = @JoinColumn(name = "task_instance_id")
  )
  @Column(name = "group_id", nullable = false)
  private Set<String> candidateGroups = new HashSet<>();


  @NotAudited
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "t_task_instance_candidate_user",
      joinColumns = @JoinColumn(name = "task_instance_id")
  )
  @Column(name = "user_id", nullable = false)
  private Set<String> candidateUsers = new HashSet<>();


  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "process_instance_id", referencedColumnName = "id")
  private ProcessInstanceEntity processInstanceId;
  @Column(name="started_at")
  private LocalDateTime startedAt;


  @Column(name="started_by", length=100)
  private String startedBy;


  @Column(name="assigned_by", length=100)
  private String assignedBy;


  @Column(name="assigned_at")
  private LocalDateTime assignedAt;


  @Column(name="priority")
  private Integer priority;


  @Column(name="ended_at")
  private LocalDateTime endedAt;


  @Column(name="ended_by")
  private String endedBy;


  @NotNull(message = "status is mandatory")
  @Enumerated(EnumType.STRING)
  @Column(name="status", nullable = false)
  private TaskInstanceStatus status;

  @OneToMany(mappedBy = "taskInstanceId")
  private List<TaskInstanceEventEntity> taskinstanceevents = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "variables", columnDefinition = "jsonb")
  private Map<String, Object> variables = new HashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "forms", columnDefinition = "jsonb")
  private Map<String, Object> forms = new HashMap<>();

  @Column(name="due_date")
  private LocalDateTime dueDate;

}
