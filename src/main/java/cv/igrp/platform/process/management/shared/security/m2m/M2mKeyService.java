package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.M2mApiKeyEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.M2mApiKeyEntityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Management operations over M2M API keys (docs/SPEC_M2M_AUTHORIZATION.md §6.2). Super-admin only —
 * enforced at the route gate in SecurityConfig, structurally out of reach of any M2M key.
 */
@Service
public class M2mKeyService {

  /** MODULE:action — role/group strings can never be granted through the permissions column (M-11). */
  private static final Pattern PERMISSION_FORMAT = Pattern.compile("^[A-Z0-9_.]+:[a-z_]+$");
  private static final Pattern CLIENT_NAME_FORMAT = Pattern.compile("^[a-z0-9._-]+$");

  private final M2mApiKeyEntityRepository repository;
  private final M2mKeyCodec codec;
  private final Duration rotateGrace;

  public record CreatedKey(UUID id, String clientName, String plaintextKey) { }
  public record KeySummary(UUID id, String clientName, String keyPrefix, String permissions,
                           String email, boolean active, Instant expiresAt, Instant createdAt,
                           Instant lastUsedAt, Instant revokedAt) { }

  public M2mKeyService(M2mApiKeyEntityRepository repository,
                       M2mKeyCodec codec,
                       @Value("${igrp.authorization.m2m.rotate-grace:7d}") Duration rotateGrace) {
    this.repository = repository;
    this.codec = codec;
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
    repository.save(entity);

    return new CreatedKey(entity.getId(), clientName, plaintext);
  }

  @Transactional(readOnly = true)
  public List<KeySummary> list() {
    return repository.findAll().stream()
        .map(e -> new KeySummary(e.getId(), e.getClientName(), e.getKeyPrefix(), e.getPermissions(),
            e.getEmail(), e.isActive(), e.getExpiresAt(), e.getCreatedAt(), e.getLastUsedAt(),
            e.getRevokedAt()))
        .toList();
  }

  @Transactional
  public void revoke(UUID id) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("m2m key not found: " + id));
    entity.setActive(false);
    entity.setRevokedAt(Instant.now());
    repository.save(entity);
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
    repository.save(old);

    return replacement;
  }

}
