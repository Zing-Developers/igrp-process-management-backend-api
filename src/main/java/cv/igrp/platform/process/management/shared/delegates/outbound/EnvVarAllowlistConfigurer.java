package cv.igrp.platform.process.management.shared.delegates.outbound;

import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * Installs the process-referenceable environment-variable allowlist into {@link EnvVarUtil} at startup,
 * so every delegate's {@code $[VAR]} resolution is bounded by {@code igrp.delegate.outbound.allowed-env-vars}.
 */
@Component
public class EnvVarAllowlistConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(EnvVarAllowlistConfigurer.class);

  public EnvVarAllowlistConfigurer(OutboundGuardProperties properties) {
    var patterns = properties.allowedEnvVars();
    EnvVarUtil.configureAllowlist(toPredicate(patterns));
    LOGGER.info("Process-referenceable environment variables restricted to: {}",
        patterns == null || patterns.isEmpty() ? "(none)" : patterns);
  }

  /** Matches exact names or {@code PREFIX*} wildcards, case-sensitive (env var names are). */
  static Predicate<String> toPredicate(List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return name -> false;
    }
    return name -> {
      if (name == null) {
        return false;
      }
      for (String raw : patterns) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        var pattern = raw.trim();
        if (pattern.endsWith("*")) {
          if (name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
          }
        } else if (name.equals(pattern)) {
          return true;
        }
      }
      return false;
    };
  }
}
