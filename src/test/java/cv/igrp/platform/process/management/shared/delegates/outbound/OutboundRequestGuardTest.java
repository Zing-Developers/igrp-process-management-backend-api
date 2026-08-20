package cv.igrp.platform.process.management.shared.delegates.outbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundRequestGuardTest {

  private static OutboundRequestGuard guard(boolean https, List<String> hosts, List<String> extra) {
    return new OutboundRequestGuard(new OutboundGuardProperties(https, hosts, extra, 1024));
  }

  /** No allowlist: public hosts pass, every internal range is blocked. */
  private final OutboundRequestGuard open = guard(false, List.of(), List.of());

  @Test
  @DisplayName("loopback, private, link-local, metadata, CGNAT and ULA destinations are blocked")
  void blocksInternalAddressSpace() {
    for (String url : List.of(
        "http://127.0.0.1/x", "http://localhost/x", "http://0.0.0.0/x",
        "http://10.1.2.3/x", "http://172.16.0.9/x", "http://192.168.1.1/x",
        "http://169.254.169.254/latest/meta-data", // cloud metadata
        "http://100.64.10.10/x",                    // CGNAT
        "http://[::1]/x", "http://[fc00::1]/x", "http://[fd12::1]/x")) {
      assertThatThrownBy(() -> open.validate(url))
          .as(url)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blocked address range");
    }
  }

  @Test
  void blocksNonHttpSchemesUserInfoAndMissingHost() {
    assertThatThrownBy(() -> open.validate("ftp://example.com/x"))
        .hasMessageContaining("http or https");
    assertThatThrownBy(() -> open.validate("file:///etc/passwd"))
        .hasMessageContaining("http or https");
    assertThatThrownBy(() -> open.validate("http://user:pass@example.com/x"))
        .hasMessageContaining("user info");
    assertThatThrownBy(() -> open.validate("http:///x"))
        .hasMessageContaining("no host");
  }

  @Test
  void requiresHttpsWhenConfigured() {
    var strict = guard(true, List.of("api.example.com"), List.of());
    assertThatThrownBy(() -> strict.validate("http://api.example.com/x"))
        .hasMessageContaining("https");
    assertThatCode(() -> strict.validate("https://api.example.com/x")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("with an allowlist, only listed hosts pass — and they skip resolution (may be internal)")
  void allowlistIsExclusiveAndTrusted() {
    var g = guard(false, List.of("api.example.com", "*.irn.lan", "wiremock-irn"), List.of());
    assertThatCode(() -> g.validate("https://api.example.com/v1")).doesNotThrowAnyException();
    assertThatCode(() -> g.validate("https://svc.irn.lan/v1")).doesNotThrowAnyException();
    assertThatCode(() -> g.validate("http://wiremock-irn:8080/x")).doesNotThrowAnyException();
    assertThatThrownBy(() -> g.validate("https://evil.example.org/x"))
        .hasMessageContaining("not allowlisted");
    // wildcard must not match the bare suffix owner’s sibling
    assertThatThrownBy(() -> g.validate("https://irn.lan.evil.org/x"))
        .hasMessageContaining("not allowlisted");
  }

  @Test
  void unresolvableHostsAreBlockedWithoutAnAllowlist() {
    assertThatThrownBy(() -> open.validate("http://definitely-not-a-real-host.invalid/x"))
        .hasMessageContaining("cannot be resolved");
  }

  @Test
  @DisplayName("credential and proxy headers from process variables are dropped; others pass")
  void filtersDangerousHeaders() {
    var g = guard(false, List.of(), List.of());
    HttpHeaders h = g.buildHeaders(Map.of(
        "Authorization", "Bearer stolen",
        "Cookie", "session=1",
        "X-Forwarded-For", "1.2.3.4",
        "Proxy-Authorization", "x",
        "X-Correlation-Id", "abc"), "global");
    assertThat(h.containsKey("Authorization")).isFalse();
    assertThat(h.containsKey("Cookie")).isFalse();
    assertThat(h.containsKey("X-Forwarded-For")).isFalse();
    assertThat(h.containsKey("Proxy-Authorization")).isFalse();
    assertThat(h.getFirst("X-Correlation-Id")).isEqualTo("abc");
  }

  @Test
  void allowedExtraHeadersOverrideTheBlocklist() {
    var g = guard(false, List.of(), List.of("Authorization"));
    HttpHeaders h = g.buildHeaders(Map.of("Authorization", "Bearer deliberate"), "global");
    assertThat(h.getFirst("Authorization")).isEqualTo("Bearer deliberate");
  }

  @Test
  @DisplayName("with no custom headers the platform token fallback is preserved")
  void globalTokenFallback() {
    var g = guard(false, List.of(), List.of());
    assertThat(g.buildHeaders(Map.of(), "tok").getFirst("Authorization")).isEqualTo("Bearer tok");
    assertThat(g.buildHeaders(Map.of(), "").containsKey("Authorization")).isFalse();
  }

}
