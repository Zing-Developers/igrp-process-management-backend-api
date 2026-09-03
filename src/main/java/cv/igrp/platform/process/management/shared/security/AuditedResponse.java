package cv.igrp.platform.process.management.shared.security;

import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;

import java.time.LocalDateTime;

/**
 * A response DTO carrying the platform audit trio — time, raw principal, enriched profile — for the
 * created/updated pair. Lombok's {@code @Data} on the DTOs provides every method; the interface only
 * lets {@link AuditUserResponseAdvice} and the mapper helper treat all of them alike.
 */
public interface AuditedResponse {

  String getCreatedBy();
  void setCreatedBy(String createdBy);

  String getUpdatedBy();
  void setUpdatedBy(String updatedBy);

  LocalDateTime getCreatedAt();
  void setCreatedAt(LocalDateTime createdAt);

  LocalDateTime getUpdatedAt();
  void setUpdatedAt(LocalDateTime updatedAt);

  void setUserProfileCreatedBy(UserProfileDTO profile);
  void setUserProfileUpdatedBy(UserProfileDTO profile);

}
