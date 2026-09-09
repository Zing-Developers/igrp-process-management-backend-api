package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 201 body of POST /m2m-keys and POST /m2m-keys/{id}/rotate. {@code key} is the plaintext and exists
 * only in this response — never logged, never persisted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class M2mKeyCreatedDTO {

  private UUID id;

  private String clientName;

  private String key;

  private String createdBy;

  private UserProfileDTO userProfileCreatedBy;

}
