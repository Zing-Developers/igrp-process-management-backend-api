package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional body of POST /failed-jobs/{jobId}/retry. Modelled in .igrpstudio/shared/dto. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class FailedJobRetryRequestDTO {

  /** Attempts the job gets back; null = IGRP_ASYNC_JOB_RETRIES. */
  private Integer retries;

}
