package cv.igrp.platform.process.management.shared.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Permissions granted to the holder of a validated Keycloak token by its email claim
 * (docs/SPEC_EMAIL_ACCESS_MAPPING.md). No secret is stored: the token is the credential, this row is
 * only the grant. The partial unique index (email WHERE active) lives in V10, not in JPA.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_email_access_mapping")
public class EmailAccessMappingEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  /** The token's email claim, trimmed and lower-cased on write and on lookup. */
  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "description")
  private String description;

  /** Free text for the operators: who asked for the grant, ticket, contact. Never part of any decision. */
  @Column(name = "notes")
  private String notes;

  /** Comma-separated MODULE:action list — validated on write here, re-checked on read by the framework. */
  @Column(name = "permissions", nullable = false)
  private String permissions;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private String updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "revoked_by")
  private String revokedBy;

  @Column(name = "revoked_at")
  private Instant revokedAt;

}
