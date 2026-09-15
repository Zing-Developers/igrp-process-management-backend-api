package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.shared.application.dto.PageDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Page of GET /email-access-mappings, the platform's page shape plus the rows. Modelled in .igrpstudio/shared/dto. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class EmailAccessMappingListPageDTO extends PageDTO {

  private List<EmailAccessMappingDTO> content = new ArrayList<>();

}
