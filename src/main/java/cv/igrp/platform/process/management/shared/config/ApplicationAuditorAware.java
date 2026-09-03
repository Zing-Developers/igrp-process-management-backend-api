package cv.igrp.platform.process.management.shared.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;


public class ApplicationAuditorAware implements AuditorAware<String> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAuditorAware.class);

  // Aligned with the Studio's auditor: same fallback name, same anonymous handling
  private static final String SYSTEM_FALLBACK = "system";

  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.ofNullable(getCurrentSubjectName()).filter(s -> !s.isBlank());
  }


  private String getCurrentSubjectName() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      LOGGER.debug("No authenticated user found, falling back to system account");
      return SYSTEM_FALLBACK;
    }

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String sub = jwt.getClaimAsString("sub");
      if (sub != null && !sub.isBlank()) {
        LOGGER.debug("Resolved auditor from JWT sub: {}", sub);
        return sub;
      }
    }

    if (authentication != null) {
      String name = authentication.getName();
      if (name != null && !name.isBlank()) {
        LOGGER.debug("Resolved auditor from authentication name: {}", name);
        return name;
      }
    }

    LOGGER.debug("No authenticated user found, falling back to system account");
    return SYSTEM_FALLBACK;
  }

}
