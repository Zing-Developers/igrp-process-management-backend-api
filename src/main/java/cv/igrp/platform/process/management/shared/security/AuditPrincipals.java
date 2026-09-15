package cv.igrp.platform.process.management.shared.security;

import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Audit-user enrichment shared by the security consoles (M2M keys, email access mappings): a stored
 * principal may be a sub or an email, so both are tried in one batch lookup. Dates follow the platform's
 * zone-less LocalDateTime (see AuditEntity).
 */
@Component
public class AuditPrincipals {

  private final UserProfileRepository userProfileRepository;
  private final UserProfileMapper userProfileMapper;

  public AuditPrincipals(UserProfileRepository userProfileRepository, UserProfileMapper userProfileMapper) {
    this.userProfileRepository = userProfileRepository;
    this.userProfileMapper = userProfileMapper;
  }

  public static LocalDateTime local(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }

  /** Profiles keyed by both sub and email; absent principals simply have no entry. */
  public Map<String, UserProfileDTO> profilesOf(Set<String> principals) {
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

  public UserProfileDTO profileOf(String principal) {
    return principal == null ? null : profilesOf(Set.of(principal)).get(principal);
  }

}
