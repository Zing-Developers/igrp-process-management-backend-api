package cv.igrp.platform.process.management.shared.util;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code $[VAR_NAME]} references in process-supplied values against the server environment.
 *
 * <p>The reference is the sanctioned way for a process definition to use a server-side credential
 * without embedding the secret in the BPMN. To stop a process author from reading arbitrary server
 * secrets (e.g. {@code $[POSTGRES_PASSWORD]}) and exfiltrating them through a webhook, resolution is
 * restricted to an allowlist configured once at startup ({@link #configureAllowlist}); referencing a
 * variable outside it fails with a clear error naming the property to change.
 */
public class EnvVarUtil {

  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\[([A-Za-z_][A-Za-z0-9_]*)\\]");

  /** Permissive until the Spring context configures it at startup; production always configures it. */
  private static volatile Predicate<String> allowlist = name -> true;

  /**
   * Sets which environment variable names process definitions may reference. Called once at startup
   * from the delegate configuration.
   *
   * @param predicate accepts a variable name and returns whether it may be referenced
   */
  public static void configureAllowlist(Predicate<String> predicate) {
    allowlist = predicate == null ? name -> true : predicate;
  }

  /** Whether the given variable name is referenceable under the active allowlist. */
  public static boolean isAllowed(String varName) {
    return varName != null && allowlist.test(varName);
  }

  public static String resolveEnvVars(String value, String fieldName) {
    return resolveEnvVars(value, fieldName, System::getenv, allowlist);
  }

  static String resolveEnvVars(String value, String fieldName, Function<String, String> envLookup) {
    return resolveEnvVars(value, fieldName, envLookup, allowlist);
  }

  static String resolveEnvVars(String value, String fieldName,
                               Function<String, String> envLookup, Predicate<String> allow) {
    if (value == null) {
      return null;
    }

    Matcher matcher = ENV_VAR_PATTERN.matcher(value);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String varName = matcher.group(1);
      if (!allow.test(varName)) {
        throw new RuntimeException(
            "Environment variable '" + varName + "' referenced in field '" + fieldName
                + "' is not referenceable from process definitions; add it to "
                + "igrp.delegate.outbound.allowed-env-vars if this is intended");
      }
      String envValue = envLookup.apply(varName);
      if (envValue == null) {
        throw new RuntimeException(
            "Environment variable '" + varName + "' referenced in field '" + fieldName + "' is not set");
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
    }
    matcher.appendTail(result);

    return result.toString();
  }

}
