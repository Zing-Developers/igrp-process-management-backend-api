package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.framework.process.runtime.auth.core.access.EmailAccessResolver;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.EmailAccessMappingEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the email access mapping against {@code t_email_access_mapping}. Runs once per session-less
 * request (one indexed lookup); no cache, so a revocation is effective on the next request.
 * The framework re-checks every returned permission against the MODULE:action format.
 */
// ponytail: uncached, one indexed lookup per request; add a short Caffeine TTL only if it shows up in latency
@Component
public class DbEmailAccessResolver implements EmailAccessResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbEmailAccessResolver.class);

  private final EmailAccessMappingEntityRepository repository;

  public DbEmailAccessResolver(EmailAccessMappingEntityRepository repository) {
    this.repository = repository;
  }

  @Override
  public Set<String> resolve(String email) {
    final var normalised = email.trim().toLowerCase(Locale.ROOT);
    final var mapping = repository.findByEmailAndActiveTrue(normalised).orElse(null);
    if (mapping == null) {
      return Set.of();
    }
    if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(Instant.now())) {
      LOGGER.atDebug()
          .addKeyValue("event", "email_access_expired")
          .addKeyValue("access.mapping_id", mapping.getId().toString())
          .log("Email access mapping [{}] has expired", mapping.getId());
      return Set.of();
    }
    LOGGER.atDebug()
        .addKeyValue("event", "email_access_used")
        .addKeyValue("access.mapping_id", mapping.getId().toString())
        .log("Email access mapping [{}] used", mapping.getId());
    return split(mapping.getPermissions());
  }

  static Set<String> split(String permissions) {
    final var set = new LinkedHashSet<String>();
    Arrays.stream(permissions.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
    return set;
  }

}
