package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKey;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKeyResolver;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.M2mApiKeyEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.M2mApiKeyEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Database-backed implementation of the framework's {@link M2mKeyResolver} SPI: HMAC the presented
 * key, look it up, honour revocation and expiry. Backs the M2M authentication path
 * (docs/SPEC_M2M_AUTHORIZATION.md).
 *
 * <p>The {@code last_used_at} stamp is throttled (≥60s) and best-effort in its own transaction —
 * a write failure never affects the authentication decision.
 */
@Component
public class DbM2mKeyResolver implements M2mKeyResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbM2mKeyResolver.class);
  private static final Duration LAST_USED_THROTTLE = Duration.ofSeconds(60);

  private final M2mApiKeyEntityRepository repository;
  private final M2mKeyCodec codec;
  private final TransactionTemplate transactionTemplate;

  public DbM2mKeyResolver(M2mApiKeyEntityRepository repository,
                          M2mKeyCodec codec,
                          TransactionTemplate transactionTemplate) {
    this.repository = repository;
    this.codec = codec;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Optional<M2mKey> resolve(String rawKey) {

    final var entity = repository.findByKeyHash(codec.hash(rawKey)).orElse(null);
    if (entity == null) {
      // unknown key: the introspector's WARN carries the presented prefix — nothing trustworthy to add
      return Optional.empty();
    }
    if (!entity.isActive()) {
      logRejected(entity, "revoked");
      return Optional.empty();
    }
    final var now = Instant.now();
    if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now)) {
      logRejected(entity, "expired");
      return Optional.empty();
    }

    stampLastUsedBestEffort(entity, now);

    final Set<String> permissions = Arrays.stream(entity.getPermissions().split(","))
        .map(String::trim)
        .filter(p -> !p.isEmpty())
        .collect(Collectors.toSet());

    return Optional.of(new M2mKey(entity.getClientName(), permissions));
  }

  /** One structured WARN per rejected authentication attempt — auditable at production log levels. */
  private static void logRejected(M2mApiKeyEntity entity, String reason) {
    LOGGER.atWarn()
        .addKeyValue("event", "m2m_auth_rejected")
        .addKeyValue("m2m.key_id", entity.getId().toString())
        .addKeyValue("m2m.client_name", entity.getClientName())
        .addKeyValue("m2m.key_prefix", entity.getKeyPrefix())
        .addKeyValue("m2m.reject_reason", reason)
        .log("M2M key [{}] rejected: {} (client {})", entity.getKeyPrefix(), reason, entity.getClientName());
  }

  private void stampLastUsedBestEffort(M2mApiKeyEntity entity, Instant now) {
    final var last = entity.getLastUsedAt();
    if (last != null && last.plus(LAST_USED_THROTTLE).isAfter(now)) {
      return;
    }
    // throttled by the same gate as the stamp: at most one usage record per key per 60s
    LOGGER.atInfo()
        .addKeyValue("event", "m2m_key_used")
        .addKeyValue("m2m.key_id", entity.getId().toString())
        .addKeyValue("m2m.client_name", entity.getClientName())
        .addKeyValue("m2m.key_prefix", entity.getKeyPrefix())
        .log("M2M key used by client [{}] (prefix {})", entity.getClientName(), entity.getKeyPrefix());
    try {
      transactionTemplate.executeWithoutResult(status -> repository.stampLastUsed(entity.getId(), now));
    } catch (RuntimeException e) {
      // best-effort by design, but a decaying last_used_at audit column deserves visibility
      LOGGER.warn("Could not stamp last_used_at for m2m key [{}]: {}", entity.getKeyPrefix(), e.getMessage());
    }
  }

}
