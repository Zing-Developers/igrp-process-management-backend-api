package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskEventType;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class TaskInstanceEvent {

  private cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit;

  public void setAudit(cv.igrp.platform.process.management.shared.domain.models.AuditTrail audit) {
    this.audit = audit;
  }
    private final Identifier id;
    private final Identifier taskInstanceId;
    private final TaskEventType eventType;
    private LocalDateTime performedAt;
    private final Code performedBy;
    private final String note;
    private final TaskInstanceStatus status;
    private UserProfile userProfilePerformedBy;


    @Builder
    public TaskInstanceEvent(
                Identifier id,
                Identifier taskInstanceId,
                TaskEventType eventType,
                LocalDateTime performedAt,
                Code performedBy,
                String note,
                TaskInstanceStatus status,
                UserProfile userProfilePerformedBy
    ) {
        this.id = id == null ? Identifier.generate() : id;
        this.taskInstanceId = Objects.requireNonNull(taskInstanceId, "TaskInstanceId cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "EventType cannot be null");
        this.performedAt = performedAt;
        this.performedBy = Objects.requireNonNull(performedBy, "PerformedBy cannot be null");
        this.note = note!=null && !note.isBlank() ? note.trim() : null;
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.userProfilePerformedBy = userProfilePerformedBy;
    }


    public void create() {
        this.performedAt = LocalDateTime.now();
    }

  public void resolveUserProfilePerformedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfilePerformedBy = userProfile;
  }


}
