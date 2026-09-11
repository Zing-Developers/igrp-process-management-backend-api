package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.framework.process.runtime.auth.core.adapter.PermissionFormat;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingDTO;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.EmailAccessMappingEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Management operations over email access mappings (docs/SPEC_EMAIL_ACCESS_MAPPING.md). Super-admin
 * only — enforced at the route gate in SecurityConfig, out of reach of any mapped token or M2M key.
 */
@Service
public class EmailAccessMappingService {

  // Grant lifecycle events are security audit records: one structured line per mutation.
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailAccessMappingService.class);

  private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final EmailAccessMappingEntityRepository repository;
  private final UserProfileRepository userProfileRepository;
  private final UserProfileMapper userProfileMapper;

  public EmailAccessMappingService(EmailAccessMappingEntityRepository repository,
                                   UserProfileRepository userProfileRepository,
                                   UserProfileMapper userProfileMapper) {
    this.repository = repository;
    this.userProfileRepository = userProfileRepository;
    this.userProfileMapper = userProfileMapper;
  }

  @Transactional
  public EmailAccessMappingDTO create(String email, List<String> permissions, String description,
                                      Instant expiresAt, String createdBy) {
    final var normalised = normalise(email);
    final var granted = validated(permissions, normalised, createdBy);
    warnIfHuman(normalised);

    final var entity = new EmailAccessMappingEntity();
    entity.setId(UUID.randomUUID());
    entity.setEmail(normalised);
    entity.setDescription(blankToNull(description));
    entity.setPermissions(String.join(",", granted));
    entity.setActive(true);
    entity.setExpiresAt(expiresAt);
    entity.setCreatedBy(createdBy);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(entity.getCreatedAt());
    entity.setUpdatedBy(createdBy);
    saveOrConflict(entity);

    LOGGER.atInfo()
        .addKeyValue("event", "email_access_created")
        .addKeyValue("access.mapping_id", entity.getId().toString())
        .addKeyValue("access.email", normalised)
        .addKeyValue("access.permissions", entity.getPermissions())
        .addKeyValue("enduser.id", createdBy)
        .log("Email access mapping created for [{}]", normalised);

    return toDto(entity, profilesOf(Set.of(createdBy)));
  }

  @Transactional(readOnly = true)
  public List<EmailAccessMappingDTO> list() {
    final var entities = repository.findAll();
    final var principals = entities.stream()
        .flatMap(e -> Stream.of(e.getCreatedBy(), e.getRevokedBy(), e.getUpdatedBy()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    final var profiles = profilesOf(principals);
    return entities.stream().map(e -> toDto(e, profiles)).toList();
  }

  @Transactional
  public EmailAccessMappingDTO update(UUID id, List<String> permissions, String description,
                                      Instant expiresAt, String updatedBy) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("email access mapping not found: " + id));
    if (!entity.isActive()) {
      throw new IllegalArgumentException("email access mapping is revoked; create a new one");
    }
    final var granted = validated(permissions, entity.getEmail(), updatedBy);
    entity.setPermissions(String.join(",", granted));
    entity.setDescription(blankToNull(description));
    entity.setExpiresAt(expiresAt);
    entity.setUpdatedAt(Instant.now());
    entity.setUpdatedBy(updatedBy);
    repository.save(entity);

    LOGGER.atInfo()
        .addKeyValue("event", "email_access_updated")
        .addKeyValue("access.mapping_id", entity.getId().toString())
        .addKeyValue("access.email", entity.getEmail())
        .addKeyValue("access.permissions", entity.getPermissions())
        .addKeyValue("enduser.id", updatedBy)
        .log("Email access mapping updated for [{}]", entity.getEmail());

    return toDto(entity, profilesOf(Set.of(entity.getCreatedBy(), updatedBy)));
  }

  @Transactional
  public void revoke(UUID id, String revokedBy) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("email access mapping not found: " + id));
    entity.setActive(false);
    entity.setRevokedAt(Instant.now());
    entity.setRevokedBy(revokedBy);
    entity.setUpdatedAt(entity.getRevokedAt());
    entity.setUpdatedBy(revokedBy);
    repository.save(entity);

    LOGGER.atInfo()
        .addKeyValue("event", "email_access_revoked")
        .addKeyValue("access.mapping_id", entity.getId().toString())
        .addKeyValue("access.email", entity.getEmail())
        .addKeyValue("enduser.id", revokedBy)
        .log("Email access mapping revoked for [{}] — effective on the next request", entity.getEmail());
  }

  // --- rules ---

  private static String normalise(String email) {
    if (email == null || !EMAIL_FORMAT.matcher(email.trim()).matches()) {
      throw new IllegalArgumentException("email must be a valid address");
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static List<String> validated(List<String> permissions, String email, String actor) {
    if (permissions == null || permissions.isEmpty()) {
      throw new IllegalArgumentException("at least one permission is required");
    }
    for (String permission : permissions) {
      if (!PermissionFormat.isValid(permission)) {
        LOGGER.atWarn()
            .addKeyValue("event", "email_access_permission_rejected")
            .addKeyValue("access.email", email)
            .addKeyValue("access.permission", String.valueOf(permission))
            .addKeyValue("enduser.id", actor)
            .log("Email access mapping rejected for [{}]: invalid permission [{}]", email, permission);
        throw new IllegalArgumentException(
            "invalid permission '" + permission + "': expected MODULE:action (roles are not allowed)");
      }
    }
    return permissions.stream().map(String::trim).distinct().toList();
  }

  /** The partial unique index is the real guard; here it just becomes a 400 instead of a 500. */
  private void saveOrConflict(EmailAccessMappingEntity entity) {
    try {
      repository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("email already has an active mapping: " + entity.getEmail());
    }
  }

  /**
   * Tripwire, not a rule: mappings are meant for dedicated service-account addresses. A known human
   * profile with this email means a person would get the grant by dropping the session cookie, and
   * would share attribution with the service; worth a warning in the audit log.
   */
  private void warnIfHuman(String email) {
    userProfileRepository.findBySubjectOrEmail(email, email)
        .filter(p -> p.getUsername() != null && !p.getUsername().startsWith("service-account-"))
        .ifPresent(p -> LOGGER.atWarn()
            .addKeyValue("event", "email_access_human_email")
            .addKeyValue("access.email", email)
            .log("Email access mapping created for an email that belongs to a human profile [{}]", p.getUsername()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** The platform serializes dates as zone-less LocalDateTime (see AuditEntity) — match it. */
  static LocalDateTime local(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  private static EmailAccessMappingDTO toDto(EmailAccessMappingEntity e, Map<String, UserProfileDTO> profiles) {
    return new EmailAccessMappingDTO(e.getId(), e.getEmail(), e.getDescription(),
        List.copyOf(DbEmailAccessResolver.split(e.getPermissions())), e.isActive(), local(e.getExpiresAt()),
        local(e.getCreatedAt()), e.getCreatedBy(), profiles.get(e.getCreatedBy()),
        local(e.getUpdatedAt()), e.getUpdatedBy(), profiles.get(e.getUpdatedBy()),
        local(e.getRevokedAt()), e.getRevokedBy(), profiles.get(e.getRevokedBy()));
  }

  /** Batch audit-user enrichment: the principal may be a sub or an email, so both are tried. */
  private Map<String, UserProfileDTO> profilesOf(Set<String> principals) {
    final var lookup = new HashMap<String, UserProfileDTO>();
    final var keys = principals.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (keys.isEmpty()) {
      return lookup;
    }
    for (UserProfile p : userProfileRepository.findBySubjectOrEmails(keys, keys)) {
      final var dto = userProfileMapper.toDTO(p);
      if (p.getSub() != null) lookup.put(p.getSub(), dto);
      if (p.getEmail() != null) lookup.put(p.getEmail(), dto);
    }
    return lookup;
  }

}
