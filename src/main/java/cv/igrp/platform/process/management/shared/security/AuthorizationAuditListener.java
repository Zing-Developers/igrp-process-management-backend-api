package cv.igrp.platform.process.management.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Emits one structured WARN per authorization denial, so every 403 is observable: who was denied,
 * on which route, and which authorities would have granted access.
 *
 * <p>The SLF4J key-value pairs become OTLP log attributes through the OpenTelemetry logback appender
 * (see {@code otel.instrumentation.logback-appender.*} in application.properties), and the log record
 * carries the active trace context, so a denial correlates directly with its request trace.
 *
 * <p>Events are published by the {@code AuthorizationEventPublisher} bean in {@link SecurityConfig};
 * Spring Security publishes denials only, so granted requests add no log noise.
 */
@Component
public class AuthorizationAuditListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationAuditListener.class);

  @EventListener
  public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {

    // authorizeHttpRequests wraps the HttpServletRequest itself; method-security managers use
    // RequestAuthorizationContext — resolve the request from either shape
    final HttpServletRequest request = switch (event.getObject()) {
      case HttpServletRequest r -> r;
      case RequestAuthorizationContext c -> c.getRequest();
      default -> null;
    };
    final var method = request != null ? request.getMethod() : "?";
    final var path = request != null ? request.getRequestURI() : String.valueOf(event.getObject());

    final var required = event.getAuthorizationResult() instanceof AuthorityAuthorizationDecision decision
        ? decision.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","))
        : "";

    String user = "unknown";
    int held = -1;
    try {
      Authentication authentication = event.getAuthentication().get();
      if (authentication != null) {
        user = authentication.getName();
        held = authentication.getAuthorities().size();
      }
    } catch (RuntimeException ignored) {
      // no authentication available for this denial; keep "unknown"
    }

    LOGGER.atWarn()
        .addKeyValue("event", "authorization_denied")
        .addKeyValue("http.request.method", method)
        .addKeyValue("url.path", path)
        .addKeyValue("enduser.id", user)
        .addKeyValue("security.required_authorities", required)
        .addKeyValue("security.granted_authority_count", held)
        .log("Authorization denied: {} {} for [{}] (required any of: {})", method, path, user, required);
  }

}
