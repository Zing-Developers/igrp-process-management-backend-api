package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** POST/PUT /email-access-mappings body (docs/SPEC_EMAIL_ACCESS_MAPPING.md). Modelled in .igrpstudio/shared/dto. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class EmailAccessMappingRequestDTO {

  // ignored on PUT: the email of a mapping never changes, revoke and create instead
  private String email;

  private List<String> permissions;

  private String description;

  private String notes;

  // zone-less LocalDateTime, the datetime shape of every other endpoint
  private LocalDateTime expiresAt;

}
