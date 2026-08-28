package cv.igrp.platform.process.management.shared.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A machine-to-machine API key (docs/SPEC_M2M_AUTHORIZATION.md). Stores only the HMAC-SHA-256 of the
 * key; the plaintext is shown once at creation and never persisted or logged.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_m2m_api_key",
    indexes = @Index(name = "idx_m2m_api_key_client_name", columnList = "client_name"))
public class M2mApiKeyEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "client_name", nullable = false)
  private String clientName;

  @Column(name = "key_prefix", nullable = false)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, unique = true)
  private String keyHash;

  /** Comma-separated MODULE:action list — validated on write and on read, never ROLE_*. */
  @Column(name = "permissions", nullable = false)
  private String permissions;

  /** Contact/owner metadata only — never an identity, never logged. */
  @Column(name = "email")
  private String email;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  /** Audit: the principal that revoked the key (null while active). */
  @Column(name = "revoked_by")
  private String revokedBy;

}
