package cv.igrp.platform.process.management.shared.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvVarUtilTest {

  private static final Map<String, String> TEST_ENV = Map.of(
      "BACKEND_URL", "https://api.example.com",
      "API_TOKEN", "secret-123",
      "PORT", "8080"
  );

  private String resolve(String value, String fieldName) {
    return EnvVarUtil.resolveEnvVars(value, fieldName, TEST_ENV::get);
  }

  @Test
  void nullInput_returnsNull() {
    assertNull(resolve(null, "field"));
  }

  @Test
  void emptyString_returnsEmpty() {
    assertEquals("", resolve("", "field"));
  }

  @Test
  void noPlaceholders_returnsUnchanged() {
    assertEquals("https://example.com/api", resolve("https://example.com/api", "url"));
  }

  @Test
  void singlePlaceholder_replaced() {
    assertEquals("https://api.example.com", resolve("$[BACKEND_URL]", "webhookUrl"));
  }

  @Test
  void placeholderWithSurroundingText_replaced() {
    assertEquals("https://api.example.com/v1/users", resolve("$[BACKEND_URL]/v1/users", "webhookUrl"));
  }

  @Test
  void multiplePlaceholders_allReplaced() {
    assertEquals("https://api.example.com:8080", resolve("$[BACKEND_URL]:$[PORT]", "webhookUrl"));
  }

  @Test
  void placeholderInJsonValue_replaced() {
    String input = "{\"Authorization\": \"Bearer $[API_TOKEN]\"}";
    String expected = "{\"Authorization\": \"Bearer secret-123\"}";
    assertEquals(expected, resolve(input, "webhookPayloadHeader"));
  }

  @Test
  void missingEnvVar_throwsException() {
    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> resolve("$[MISSING_VAR]", "webhookUrl"));
    assertTrue(ex.getMessage().contains("MISSING_VAR"));
    assertTrue(ex.getMessage().contains("webhookUrl"));
  }

  @Test
  void adjacentPlaceholders_bothReplaced() {
    assertEquals("https://api.example.comsecret-123", resolve("$[BACKEND_URL]$[API_TOKEN]", "field"));
  }

  @Test
  void activitiExpression_notAffected() {
    assertEquals("${processVar}", resolve("${processVar}", "field"));
  }

  @Test
  void springExpression_notAffected() {
    assertEquals("#{bean.method()}", resolve("#{bean.method()}", "field"));
  }

}
