package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Row of GET /failed-jobs: an async job (e.g. a webhook) that ran out of retries. Modelled in .igrpstudio/shared/dto. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class FailedJobDTO {

  private String jobId;

  private UUID processInstanceId;

  private String processNumber;

  private String processName;

  private String engineProcessInstanceId;

  private String processDefinitionId;

  /** Engine execution of the failed task; one per failure, but not a branch id (the engine reuses executions). */
  private String executionId;

  private String activityId;

  private String exceptionMessage;

  /** Attempts so far, reprocessing included; null when the failure predates the recorder. */
  private Integer attempts;

  private LocalDateTime firstFailedAt;

  private LocalDateTime lastFailedAt;

  /** How many times it was put back on the queue from here; 0 if never. */
  private Integer reprocessCount;

  private LocalDateTime lastReprocessedAt;

  /** Who reprocessed it last (the principal claim, e.g. email). Only here, behind FAILED_JOBS:visualizar. */
  private String lastReprocessedBy;

}
