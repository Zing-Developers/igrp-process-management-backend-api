package cv.igrp.platform.process.management.shared.config;

import cv.igrp.platform.process.management.shared.domain.models.AuditTrail;
import cv.igrp.platform.process.management.shared.security.AuditedResponse;

/** Entity → model → DTO audit plumbing shared by the mappers of every bounded context. */
public final class AuditMapping {

  private AuditMapping() { }

  public static AuditTrail trail(AuditEntity entity) {
    return new AuditTrail(entity.getCreatedBy(), entity.getCreatedDate(),
        entity.getLastModifiedBy(), entity.getLastModifiedDate());
  }

  /** No-op when the model was never persisted (null trail) — the fields simply stay null. */
  public static void apply(AuditedResponse dto, AuditTrail trail) {
    if (trail == null) return;
    dto.setCreatedBy(trail.createdBy());
    dto.setCreatedAt(trail.createdAt());
    dto.setUpdatedBy(trail.updatedBy());
    dto.setUpdatedAt(trail.updatedAt());
  }

}
