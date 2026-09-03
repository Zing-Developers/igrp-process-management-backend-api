package cv.igrp.platform.process.management.shared.domain.models;

import java.time.LocalDateTime;

/**
 * Audit metadata carried from the entity's Spring auditing columns; set on a model after it is
 * rebuilt so the builders/factories stay untouched. Null on models that were never persisted.
 */
public record AuditTrail(String createdBy, LocalDateTime createdAt,
                         String updatedBy, LocalDateTime updatedAt) { }
