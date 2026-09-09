package cv.igrp.platform.process.management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Row of GET /m2m-keys — never carries the hash or the plaintext. Audit users follow the platform pattern. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class M2mKeySummaryDTO {

  private UUID id;

  private String clientName;

  private String keyPrefix;

  private String permissions;

  private String email;

  private boolean active;

  private LocalDateTime expiresAt;

  private LocalDateTime createdAt;

  private String createdBy;

  private UserProfileDTO userProfileCreatedBy;

  private LocalDateTime lastUsedAt;

  private LocalDateTime revokedAt;

  private String revokedBy;

  private UserProfileDTO userProfileRevokedBy;

  private LocalDateTime updatedAt;

  private String updatedBy;

  private UserProfileDTO userProfileUpdatedBy;

}
