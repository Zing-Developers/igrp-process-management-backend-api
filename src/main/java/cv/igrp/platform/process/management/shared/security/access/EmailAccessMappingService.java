package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.framework.process.runtime.auth.core.adapter.PermissionFormat;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingDTO;
import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingListPageDTO;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.EmailAccessMappingEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.shared.security.AuditPrincipals;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
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
  static final int NOTES_MAX = 2000;
  static final int PAGE_SIZE_DEFAULT = 20;
  static final int PAGE_SIZE_MAX = 100;

  private final EmailAccessMappingEntityRepository repository;
  private final AuditPrincipals principals;
  private final UserProfileRepository userProfileRepository;

  public EmailAccessMappingService(EmailAccessMappingEntityRepository repository,
                                   AuditPrincipals principals,
                                   UserProfileRepository userProfileRepository) {
    this.repository = repository;
    this.principals = principals;
    this.userProfileRepository = userProfileRepository;
  }

  @Transactional
  public EmailAccessMappingDTO create(String email, List<String> permissions, String description,
                                      String notes, Instant expiresAt, String createdBy) {
    final var normalised = normalise(email);
    final var granted = validated(permissions, normalised, createdBy);
    // every validation runs before anything is written, so a 400 can never leave an audit line behind
    final var cleanDescription = blankToNull(description);
    final var cleanNotes = notesOrNull(notes);
    retireExpiredOrConflict(normalised, createdBy);
    warnIfHuman(normalised);

    final var entity = new EmailAccessMappingEntity();
    entity.setId(UUID.randomUUID());
    entity.setEmail(normalised);
    entity.setDescription(cleanDescription);
    entity.setNotes(cleanNotes);
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

    return toDto(entity, principals.profilesOf(Set.of(createdBy)));
  }

  /**
   * One page, newest first. {@code status}: active (not expired), revoked, expired, or null for all;
   * {@code email}: contains, case-insensitive.
   */
  @Transactional(readOnly = true)
  public EmailAccessMappingListPageDTO list(String email, String status, Integer page, Integer size) {
    final var pageable = PageRequest.of(page == null || page < 0 ? 0 : page,
        size == null || size < 1 ? PAGE_SIZE_DEFAULT : Math.min(size, PAGE_SIZE_MAX), Sort.by(Sort.Direction.DESC, "createdAt"));
    final var result = repository.findAll(filter(email, status), pageable);
    final var principals = result.getContent().stream()
        .flatMap(e -> Stream.of(e.getCreatedBy(), e.getRevokedBy(), e.getUpdatedBy()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    final var profiles = this.principals.profilesOf(principals);
    final var dto = new EmailAccessMappingListPageDTO();
    dto.setContent(result.getContent().stream().map(e -> toDto(e, profiles)).toList());
    dto.setPageNumber(result.getNumber());
    dto.setPageSize(result.getSize());
    dto.setTotalElements(result.getTotalElements());
    dto.setTotalPages(result.getTotalPages());
    dto.setFirst(result.isFirst());
    dto.setLast(result.isLast());
    return dto;
  }

  /** Contains-match pattern with the LIKE metacharacters escaped, so "svc_a" does not match "svcxa". */
  static String likePattern(String email) {
    if (email == null || email.isBlank()) return null;
    final var escaped = email.trim().toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  static Specification<EmailAccessMappingEntity> filter(String email, String status) {
    final var now = Instant.now();
    final var needle = likePattern(email);
    final var state = status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.ROOT);
    if (state != null && !Set.of("active", "revoked", "expired").contains(state)) {
      throw new IllegalArgumentException("status must be one of active, revoked, expired");
    }
    return (root, query, cb) -> {
      final var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
      if (needle != null) predicates.add(cb.like(root.get("email"), needle, '\\'));
      if ("revoked".equals(state)) predicates.add(cb.isFalse(root.get("active")));
      if ("active".equals(state)) predicates.add(cb.and(cb.isTrue(root.get("active")),
          cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now))));
      if ("expired".equals(state)) predicates.add(cb.and(cb.isTrue(root.get("active")),
          cb.lessThanOrEqualTo(root.get("expiresAt"), now)));
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  @Transactional
  public EmailAccessMappingDTO update(UUID id, List<String> permissions, String description,
                                      String notes, Instant expiresAt, String updatedBy) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("email access mapping not found: " + id));
    if (!entity.isActive()) {
      throw new IllegalArgumentException("email access mapping is revoked; create a new one");
    }
    final var granted = validated(permissions, entity.getEmail(), updatedBy);
    entity.setPermissions(String.join(",", granted));
    entity.setDescription(blankToNull(description));
    entity.setNotes(notesOrNull(notes));
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

    // not Set.of: the creator editing their own mapping is the same principal twice
    return toDto(entity, principals.profilesOf(new HashSet<>(List.of(entity.getCreatedBy(), updatedBy))));
  }

  @Transactional
  public void revoke(UUID id, String revokedBy) {
    final var entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("email access mapping not found: " + id));
    if (!entity.isActive()) {
      return;   // already revoked: idempotent, and the original revokedBy/revokedAt stay as they were
    }
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

  /**
   * One active mapping per email. An expired one still holds that slot (active=true, expiresAt in the
   * past), which surprised the console: creating again for the same email failed. Expired mappings are
   * retired here (revoked by the actor) so the new grant takes over; a live one is a real conflict and
   * the message names it so the operator can edit or revoke it.
   */
  private void retireExpiredOrConflict(String email, String actor) {
    repository.findByEmailAndActiveTrue(email).ifPresent(existing -> {
      if (existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(Instant.now())) {
        existing.setActive(false);
        existing.setRevokedAt(Instant.now());
        existing.setRevokedBy(actor);
        existing.setUpdatedAt(existing.getRevokedAt());
        existing.setUpdatedBy(actor);
        repository.saveAndFlush(existing);
        LOGGER.atInfo()
            .addKeyValue("event", "email_access_superseded")
            .addKeyValue("access.mapping_id", existing.getId().toString())
            .addKeyValue("access.email", email)
            .addKeyValue("enduser.id", actor)
            .log("Expired email access mapping [{}] retired; a new one replaces it", existing.getId());
        return;
      }
      throw new IllegalArgumentException("email already has an active mapping (" + existing.getId()
          + "): edit it, or revoke it before creating a new one");
    });
  }

  /**
   * The partial unique index is the real guard against a race; that one violation becomes a 400, told
   * apart by the constraint name Hibernate reports. Any other integrity error (a column too narrow, a
   * missing NOT NULL value) is a server problem and surfaces as a 500 with its cause in the log.
   */
  private void saveOrConflict(EmailAccessMappingEntity entity) {
    try {
      repository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException e) {
      if (e.getCause() instanceof ConstraintViolationException cve && ACTIVE_EMAIL_INDEX.equals(cve.getConstraintName())) {
        throw new IllegalArgumentException("email already has an active mapping: " + entity.getEmail());
      }
      throw new IllegalStateException("could not save email access mapping: " + e.getMostSpecificCause().getMessage(), e);
    }
  }

  static final String ACTIVE_EMAIL_INDEX = "uq_email_access_mapping_active_email";

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

  private static String notesOrNull(String notes) {
    final var value = blankToNull(notes);
    if (value != null && value.length() > NOTES_MAX) {
      throw new IllegalArgumentException("notes must be at most " + NOTES_MAX + " characters");
    }
    return value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static EmailAccessMappingDTO toDto(EmailAccessMappingEntity e, Map<String, UserProfileDTO> profiles) {
    return new EmailAccessMappingDTO(e.getId(), e.getEmail(), e.getDescription(), e.getNotes(),
        List.copyOf(DbEmailAccessResolver.split(e.getPermissions())), e.isActive(), AuditPrincipals.local(e.getExpiresAt()),
        AuditPrincipals.local(e.getCreatedAt()), e.getCreatedBy(), profiles.get(e.getCreatedBy()),
        AuditPrincipals.local(e.getUpdatedAt()), e.getUpdatedBy(), profiles.get(e.getUpdatedBy()),
        AuditPrincipals.local(e.getRevokedAt()), e.getRevokedBy(), profiles.get(e.getRevokedBy()));
  }

}
