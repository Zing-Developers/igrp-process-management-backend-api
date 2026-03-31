package cv.igrp.platform.process.management.shared.config;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class ApplicationAuditorAware implements AuditorAware<String> {

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  private static final String SYSTEM_FALLBACK = "system-bot@irn.mj.pt";

  @Override
  public @NonNull Optional<String> getCurrentAuditor() {
    var preferredUsername = getPreferredUsername();
    return Optional.ofNullable(preferredUsername).filter(s -> !s.isBlank());
  }

  /**
   * Retrieves a username for auditing purposes.
   * Priority:
   * 1) preferred_username claim from JWT if present
   * 2) Authentication#getName() if an Authentication exists
   * 3) Fallback to system account (non-empty in non-dev/staging)
   */
  public String getPreferredUsername() {

    // In dev/staging we allow empty auditor to avoid noisy data during local testing
    if ("development".equalsIgnoreCase(activeProfile) || "staging".equalsIgnoreCase(activeProfile)) {
      return SYSTEM_FALLBACK;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String preferred = jwt.getClaimAsString("preferred_username");
      if (preferred != null && !preferred.isBlank()) {
        return preferred;
      }
    }

    if (authentication != null) {
      String name = authentication.getName();
      if (name != null && !name.isBlank()) {
        return name;
      }
    }

    // As a last resort, return a stable system value so auditing doesn't break background processing
    return SYSTEM_FALLBACK;
  }

}
