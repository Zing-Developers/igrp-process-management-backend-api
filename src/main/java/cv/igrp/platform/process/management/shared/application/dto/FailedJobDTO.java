package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  private String engineProcessInstanceId;

  private String processDefinitionId;

  private String activityId;

  private String exceptionMessage;

}
