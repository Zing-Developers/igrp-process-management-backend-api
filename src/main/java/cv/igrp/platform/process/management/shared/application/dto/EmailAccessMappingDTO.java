package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Row of GET /email-access-mappings and body of create/update. Audit users follow the platform pattern. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class EmailAccessMappingDTO {

  private UUID id;

  private String email;

  private String description;

  private String notes;

  private List<String> permissions;

  private boolean active;

  private LocalDateTime expiresAt;

  private LocalDateTime createdAt;

  private String createdBy;

  private UserProfileDTO userProfileCreatedBy;

  private LocalDateTime updatedAt;

  private String updatedBy;

  private UserProfileDTO userProfileUpdatedBy;

  private LocalDateTime revokedAt;

  private String revokedBy;

  private UserProfileDTO userProfileRevokedBy;

}
