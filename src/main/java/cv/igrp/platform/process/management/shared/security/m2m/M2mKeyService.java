package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.M2mApiKeyEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.M2mApiKeyEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Management operations over M2M API keys (docs/SPEC_M2M_AUTHORIZATION.md §6.2). Super-admin only —
 * enforced at the route gate in SecurityConfig, structurally out of reach of any M2M key.
 */
@Service
public class M2mKeyService {

  // Credential lifecycle events are first-order security audit records: one structured line per
  // mutation (key-values become OTLP log attributes). Never the plaintext key, never the email.
  private static final Logger LOGGER = LoggerFactory.getLogger(M2mKeyService.class);

  /** MODULE:action — role/group strings can never be granted through the permissions column (M-11). */
  private static final Pattern PERMISSION_FORMAT = Pattern.compile("^[A-Z0-9_.]+:[a-z_]+$");
  private static final Pattern CLIENT_NAME_FORMAT = Pattern.compile("^[a-z0-9._-]+$");

  private final M2mApiKeyEntityRepository repository;
  private final M2mKeyCodec codec;
  private final UserProfileRepository userProfileRepository;
  private final UserProfileMapper userProfileMapper;
  private final Duration rotateGrace;

  // createdBy follows the platform's audit-user pattern (see userProfileStartedBy on tasks):
  // the raw principal string plus the enriched IAM profile, null when no profile row matches.
  public record CreatedKey(UUID id, String clientName, String plaintextKey, String createdBy,
                           UserProfileDTO userProfileCreatedBy) { }
  // Dates go out as zone-less LocalDateTime — the same shape every other endpoint serializes
  // (AuditEntity/TaskInstanceDTO) — so the frontend parses one format everywhere.
  public record KeySummary(UUID id, String clientName, String keyPrefix, String permissions,
                           String email, boolean active, LocalDateTime expiresAt, LocalDateTime createdAt,
                           String createdBy, UserProfileDTO userProfileCreatedBy,
                           LocalDateTime lastUsedAt, LocalDateTime revokedAt,
                           String revokedBy, UserProfileDTO userProfileRevokedBy,
                           LocalDateTime updatedAt, String updatedBy, UserProfileDTO userProfileUpdatedBy) { }

  public M2mKeyService(M2mApiKeyEntityRepository repository,
                       M2mKeyCodec codec,
                       UserProfileRepository userProfileRepository,
                       UserProfileMapper userProfileMapper,
                       @Value("${igrp.authorization.m2m.rotate-grace:7d}") Duration rotateGrace) {
    this.repository = repository;
    this.codec = codec;
    this.userProfileRepository = userProfileRepository;
    this.userProfileMapper = userProfileMapper;
    this.rotateGrace = rotateGrace;
  }

  @Transactional
  public CreatedKey create(String clientName, List<String> permissions, String email,
                           Instant expiresAt, String createdBy) {

    if (clientName == null || !CLIENT_NAME_FORMAT.matcher(clientName).matches()) {
      throw new IllegalArgumentException("client_name must be a slug (lowercase letters, digits, . _ -)");
    }
    if (permissions == null || permissions.isEmpty()) {
      throw new IllegalArgumentException("at least one permission is required");
    }
    for (String permission : permissions) {
      if (permission == null || !PERMISSION_FORMAT.matcher(permission.trim()).matches()) {
        LOGGER.atWarn()
            .addKeyValue("event", "m2m_key_permission_rejected")
            .addKeyValue("m2m.client_name", clientName)
            .addKeyValue("m2m.permission", String.valueOf(permission))
            .addKeyValue("enduser.id", createdBy)
            .log("M2M key creation rejected for client [{}]: invalid permission [{}]", clientName, permission);
        throw new IllegalArgumentException(
            "invalid permission '" + permission + "': expected MODULE:action (roles are not allowed)");
      }
    }

    final var plaintext = codec.newKey();
    final var entity = new M2mApiKeyEntity();
    entity.setId(UUID.randomUUID());
    entity.setClientName(clientName);
    entity.setKeyPrefix(codec.prefixOf(plaintext));
    entity.setKeyHash(codec.hash(plaintext));
    entity.setPermissions(String.join(",", permissions.stream().map(String::trim).toList()));
    entity.setEmail(email);
    entity.setActive(true);
    entity.setExpiresAt(expiresAt);
    entity.setCreatedBy(createdBy);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(entity.getCreatedAt());
    entity.setUpdatedBy(createdBy);
    repository.save(entity);

    LOGGER.atInfo()
        .addKeyValue("event", "m2m_key_created")
        .addKeyValue("m2m.key_id", entity.getId().toString())
        .addKeyValue("m2m.client_name", clientName)
        .addKeyValue("m2m.key_prefix", entity.getKeyPrefix())
        .addKeyValue("enduser.id", createdBy)
        .log("M2M key created for client [{}] (prefix {})", clientName, entity.getKeyPrefix());

    return new CreatedKey(entity.getId(), clientName, plaintext, createdBy, profileOf(createdBy));
  }

  @Transactional(readOnly = true)
  public List<KeySummary> list() {
    final var entities = repository.findAll();
    final var principals = entities.stream()
        .flatMap(e -> java.util.stream.Stream.of(e.getCreatedBy(), e.getRevokedBy(), e.getUpdatedBy()))
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    final var profiles = profilesOf(principals);
    return entities.stream()
        .map(e -> new KeySummary(e.getId(), e.getClientName(), e.getKeyPrefix(), e.getPermissions(),
            e.getEmail(), e.isActive(), local(e.getExpiresAt()), local(e.getCreatedAt()), e.getCreatedBy(),
            profiles.get(e.getCreatedBy()), local(e.getLastUsedAt()), local(e.getRevokedAt()),
            e.getRevokedBy(), profiles.get(e.getRevokedBy()),
            local(e.getUpdatedAt()), e.getUpdatedBy(), profiles.get(e.getUpdatedBy())))
        .toList();
  }

  /** The platform serializes dates as zone-less LocalDateTime (see AuditEntity) — match it. */
  static LocalDateTime local(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  /** Batch audit-user enrichment: the principal may be a sub or an email, so both are tried. */
  private Map<String, UserProfileDTO> profilesOf(Set<String> principals) {
    final var lookup = new HashMap<String, UserProfileDTO>();
    if (principals.isEmpty()) {
      return lookup;
    }
    for (UserProfile p : userProfileRepository.findBySubjectOrEmails(principals, principals)) {
      final var dto = userProfileMapper.toDTO(p);
      if (p.getSub() != null) lookup.put(p.getSub(), dto);
      if (p.getEmail() != null) lookup.put(p.getEmail(), dto);
    }
    return lookup;
  }

  private UserProfileDTO profileOf(String principal) {
    return principal == null ? null : profilesOf(Set.of(principal)).get(principal);
  }

  @Transactional
  public void revoke(UUID id, String revokedBy) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("m2m key not found: " + id));
    entity.setActive(false);
    entity.setRevokedAt(Instant.now());
    entity.setRevokedBy(revokedBy);
    entity.setUpdatedAt(entity.getRevokedAt());
    entity.setUpdatedBy(revokedBy);
    repository.save(entity);

    LOGGER.atInfo()
        .addKeyValue("event", "m2m_key_revoked")
        .addKeyValue("m2m.key_id", entity.getId().toString())
        .addKeyValue("m2m.client_name", entity.getClientName())
        .addKeyValue("m2m.key_prefix", entity.getKeyPrefix())
        .addKeyValue("enduser.id", revokedBy)
        .log("M2M key revoked for client [{}] (prefix {}) — effective immediately", entity.getClientName(), entity.getKeyPrefix());
  }

  /**
   * Issues a fresh key for the same client/permissions and stamps the old one with a forced expiry
   * ({@code now + rotate-grace}) so the overlap cannot live forever (M-18).
   */
  @Transactional
  public CreatedKey rotate(UUID id, String createdBy) {
    final var old = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("m2m key not found: " + id));

    final var replacement = create(
        old.getClientName(),
        List.of(old.getPermissions().split(",")),
        old.getEmail(),
        null,
        createdBy);

    old.setExpiresAt(Instant.now().plus(rotateGrace));
    old.setUpdatedAt(Instant.now());
    old.setUpdatedBy(createdBy);
    repository.save(old);

    LOGGER.atInfo()
        .addKeyValue("event", "m2m_key_rotated")
        .addKeyValue("m2m.key_id", replacement.id().toString())
        .addKeyValue("m2m.replaced_key_id", old.getId().toString())
        .addKeyValue("m2m.client_name", old.getClientName())
        .addKeyValue("m2m.old_key_expires_at", old.getExpiresAt().toString())
        .addKeyValue("enduser.id", createdBy)
        .log("M2M key rotated for client [{}]: old key (prefix {}) expires at {}", old.getClientName(), old.getKeyPrefix(), old.getExpiresAt());

    return replacement;
  }

}
