package cv.igrp.platform.process.management.shared.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Audits the configured principal claim ({@code igrp.security.principal-claim-name}) — the same
 * identity used by task operations and the M2M endpoints, in both apps. The human-readable display
 * comes from the userProfile* enrichment, which resolves by sub or email.
 */
public class ApplicationAuditorAware implements AuditorAware<String> {

  private static final String SYSTEM_FALLBACK = "system";

  @Override
  public Optional<String> getCurrentAuditor() {
    return Optional.of(getCurrentUserName());
  }

  /**
   * @return the authentication name (the configured principal claim; {@code m2m:<client>} for M2M
   * callers), the JWT {@code sub} when that claim is missing from the token, or {@code "system"}
   * for unauthenticated calls such as server-generated records
   */
  public String getCurrentUserName() {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      return SYSTEM_FALLBACK;
    }

    String name = authentication.getName();
    if (name != null && !name.isBlank()) {
      return name;
    }

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      String sub = jwtAuth.getToken().getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }

    return SYSTEM_FALLBACK;
  }

}
