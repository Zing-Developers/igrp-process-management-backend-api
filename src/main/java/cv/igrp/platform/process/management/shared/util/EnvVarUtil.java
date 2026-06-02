package cv.igrp.platform.process.management.shared.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvVarUtil {

  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\[([A-Za-z_][A-Za-z0-9_]*)\\]");

  public static String resolveEnvVars(String value, String fieldName) {
    return resolveEnvVars(value, fieldName, System::getenv);
  }

  static String resolveEnvVars(String value, String fieldName, Function<String, String> envLookup) {
    if (value == null) {
      return null;
    }

    Matcher matcher = ENV_VAR_PATTERN.matcher(value);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String varName = matcher.group(1);
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
