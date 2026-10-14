package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Page of GET /failed-jobs, the platform's page shape plus the rows. Modelled in .igrpstudio/shared/dto. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class FailedJobListPageDTO extends PageDTO {

  private List<FailedJobDTO> content = new ArrayList<>();

}
