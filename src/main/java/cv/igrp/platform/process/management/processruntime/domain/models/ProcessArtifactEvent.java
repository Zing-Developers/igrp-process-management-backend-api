package cv.igrp.platform.process.management.processruntime.domain.models;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
public class ProcessArtifactEvent {

  private String processInstanceId;
  private String artifactInstanceId;
  private String artifactId;
  private String artifactName;
  private ArtifactType type;
  private String executionId;
  private String taskId;
  private ArtifactStatus status;
  private Instant startTime;
  private Instant endTime;
  private Long duration;
  private Map<String, Object> variables;
  private String assignee;
  private String treeNumber;
  private UserProfile userProfileAssignee;
  /** Set when the activity's job failed: it then sits in the engine's jobs, not in its history. */
  private JobFailure failure;

  /**
   * A failed job, as the activity list shows it: the error, how often, when, and what to reprocess.
   * attempts counts every failed run, reprocesses included; reprocessCount how many were started by hand.
   */
  public record JobFailure(String jobId, String errorMessage, Integer attempts, Instant firstFailedAt, Instant lastFailedAt,
                           Integer reprocessCount, Instant lastReprocessedAt) {
  }

  @Builder
  public ProcessArtifactEvent(String processInstanceId,
                              String artifactInstanceId,
                              String artifactId,
                              String artifactName,
                              ArtifactType type,
                              String executionId,
                              String taskId,
                              ArtifactStatus status,
                              Instant startTime,
                              Instant endTime,
                              Long duration,
                              Map<String, Object> variables,
                              String assignee,
                              String treeNumber,
                              UserProfile userProfileAssignee,
                              JobFailure failure
  ) {
    this.processInstanceId = Objects.requireNonNull(processInstanceId, "Process instance id cannot be null");
    this.artifactInstanceId = artifactInstanceId;
    this.artifactId = artifactId;
    this.artifactName = artifactName;
    this.type = Objects.requireNonNull(type, "Type cannot be null");
    this.executionId = executionId;
    this.taskId = taskId;
    this.status = Objects.requireNonNull(status, "Status cannot be null");
    this.startTime = startTime;
    this.endTime = endTime;
    this.duration = duration;
    this.variables = variables == null ? new HashMap<>() : variables;
    this.assignee = assignee;
    this.treeNumber = treeNumber;
    this.userProfileAssignee = userProfileAssignee;
    this.failure = failure;
  }

  public void addVariables(Map<String, Object> variables) {
    this.variables.putAll(variables);
  }

  public void resolveUserProfileAssignee(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileAssignee = userProfile;
  }


  public enum ArtifactType {
    USER_TASK,
    SERVICE_TASK,
    SCRIPT_TASK,
    MANUAL_TASK,
    RECEIVE_TASK,
    SEND_TASK,
    BUSINESS_RULE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY,
    MESSAGE_INTERMEDIATE_EVENT_CATCH,
    CALL_ACTIVITY,
    SUB_PROCESS,
    OTHER;
  }

  public enum ArtifactStatus {
    CREATED,
    ASSIGNED,
    SUSPENDED,
    COMPLETED,
    CANCELLED,
    DELETED,
    PENDING,
    CURRENT,
    /** The job ran out of retries (dead letter): reprocess it from /failed-jobs. */
    FAILED,
    /** The job failed and the engine will try again. */
    RETRYING,
    /** Put back on the queue from /failed-jobs; waiting for, or in, its next run. */
    REPROCESSING;
  }

}
