package cv.igrp.platform.process.management.shared.delegates.outbound;

import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SSRF guard for delegate outbound calls: URL destinations built from process variables must not be
 * able to reach loopback, private, link-local or metadata addresses, credential headers must not be
 * injectable from process data, and responses are size-bounded.
 *
 * <p>Redirects are disabled at the HTTP client (see RestClientConfig) so a public destination cannot
 * bounce the request to a blocked address.
 */
@Component
public class OutboundRequestGuard {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboundRequestGuard.class);

  /** Headers a process variable may never set: credentials and proxy identity. */
  private static final Set<String> BLOCKED_HEADERS = Set.of(
      "authorization", "proxy-authorization", "cookie", "set-cookie",
      "host", "forwarded", "proxy-connection");

  private final OutboundGuardProperties properties;

  public OutboundRequestGuard(OutboundGuardProperties properties) {
    this.properties = properties;
  }

  /**
   * Validates an outbound URL against the SSRF policy.
   *
   * @param url the destination as built from process variables
   * @return the same URL, once accepted
   * @throws IllegalArgumentException with a client-safe message when the destination is blocked
   */
  public String validate(String url) {

    final URI uri;
    try {
      uri = URI.create(url);
    } catch (Exception e) {
      throw new IllegalArgumentException("Webhook URL is not a valid URI");
    }

    final var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("Webhook URL scheme must be http or https");
    }
    if (properties.requireHttps() && !scheme.equals("https")) {
      throw new IllegalArgumentException("Webhook URL must use https");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Webhook URL must not carry user info");
    }

    final var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Webhook URL has no host");
    }

    // An allowlisted host is trusted as configured (it may legitimately be internal) and is not
    // resolved here — the connection still goes exactly where the allowlist says.
    if (isAllowlisted(host)) {
      return url;
    }

    final var allowed = properties.allowedHosts();
    if (allowed != null && !allowed.isEmpty()) {
      LOGGER.warn("Blocked outbound call: host [{}] is not in igrp.delegate.outbound.allowed-hosts", host);
      throw new IllegalArgumentException("Webhook host is not allowlisted");
    }

    // No allowlist configured: any PUBLIC host is fine, internal address space never is.
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(IDN.toASCII(host));
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Webhook host cannot be resolved");
    }
    for (InetAddress address : addresses) {
      if (isForbidden(address)) {
        LOGGER.warn("Blocked outbound call to [{}]: resolves to non-public address [{}]",
            host, address.getHostAddress());
        throw new IllegalArgumentException("Webhook host resolves to a blocked address range");
      }
    }
    return url;
  }

  /**
   * A credential-style header (e.g. {@code Authorization}) is only allowed from a process variable
   * when its value is sourced entirely from an allowlisted server environment variable — an optional
   * auth scheme word followed by a single {@code $[VAR]} reference. A literal credential is never
   * accepted; the secret must live in the server environment, not in the BPMN.
   */
  private static final Pattern ENV_SOURCED_CREDENTIAL =
      Pattern.compile("^(?:[A-Za-z]+\\s+)?\\$\\[([A-Za-z_][A-Za-z0-9_]*)]$");

  /**
   * Builds the outbound headers from the RAW (unresolved) header map: content type, plus either the
   * filtered custom headers, or the platform token when no custom headers were supplied (the
   * pre-existing fallback). Allowed header values have their {@code $[VAR]} references resolved here,
   * after the allow/block decision so provenance can be judged on the raw value.
   */
  public HttpHeaders buildHeaders(Map<String, String> rawHeaders, String globalAuthToken) {

    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    if (rawHeaders != null && !rawHeaders.isEmpty()) {
      List<String> dropped = new ArrayList<>();
      rawHeaders.forEach((name, rawValue) -> {
        if (isHeaderAllowed(name, rawValue)) {
          headers.set(name, EnvVarUtil.resolveEnvVars(rawValue, "webhookPayloadHeader"));
        } else {
          dropped.add(name);
        }
      });
      if (!dropped.isEmpty()) {
        LOGGER.warn("Dropped blocked webhook header(s) from process variables: {}", dropped);
      }
    } else if (globalAuthToken != null && !globalAuthToken.isEmpty()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + globalAuthToken);
    }
    return headers;
  }

  /** Status + size-bounded body of an outbound response. */
  public record BoundedBody(int status, String body) {
  }

  /**
   * Reads a response with the configured size cap. Meant for use inside
   * {@code RestClient...exchange((request, response) -> guard.readBounded(response))} — exchange also
   * means non-2xx statuses are returned, not thrown, so callers see every status uniformly.
   */
  public BoundedBody readBounded(ClientHttpResponse response) throws IOException {

    final long max = properties.maxResponseBytes();

    final var declared = response.getHeaders().getContentLength();
    if (declared > max) {
      throw new IllegalStateException("Webhook response exceeds the size limit");
    }

    try (InputStream in = response.getBody()) {
      byte[] data = in.readNBytes((int) Math.min(max + 1, Integer.MAX_VALUE));
      if (data.length > max) {
        throw new IllegalStateException("Webhook response exceeds the size limit");
      }
      return new BoundedBody(response.getStatusCode().value(), new String(data, StandardCharsets.UTF_8));
    }
  }

  private boolean isAllowlisted(String host) {
    final var allowed = properties.allowedHosts();
    if (allowed == null) {
      return false;
    }
    final var candidate = host.toLowerCase(Locale.ROOT);
    for (String entry : allowed) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      final var pattern = entry.trim().toLowerCase(Locale.ROOT);
      if (pattern.startsWith("*.")) {
        if (candidate.endsWith(pattern.substring(1))) {
          return true;
        }
      } else if (candidate.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  private boolean isHeaderAllowed(String name, String rawValue) {
    if (name == null || name.isBlank()) {
      return false;
    }
    final var lower = name.toLowerCase(Locale.ROOT);
    final boolean blockedByName = lower.startsWith("x-forwarded-") || BLOCKED_HEADERS.contains(lower);
    if (!blockedByName) {
      return true;
    }
    // A blocked-by-name header passes only when explicitly configured, or when its value is a
    // server-sourced credential (an allowlisted $[VAR]) rather than a process literal.
    return isExtraAllowed(lower) || isEnvSourcedCredential(rawValue);
  }

  private boolean isEnvSourcedCredential(String rawValue) {
    if (rawValue == null) {
      return false;
    }
    var matcher = ENV_SOURCED_CREDENTIAL.matcher(rawValue.trim());
    return matcher.matches() && EnvVarUtil.isAllowed(matcher.group(1));
  }

  private boolean isExtraAllowed(String lowerName) {
    final var extra = properties.allowedExtraHeaders();
    return extra != null && extra.stream()
        .filter(e -> e != null && !e.isBlank())
        .anyMatch(e -> e.trim().toLowerCase(Locale.ROOT).equals(lowerName));
  }

  private static boolean isForbidden(InetAddress address) {
    if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
      return true;
    }
    final byte[] b = address.getAddress();
    if (b.length == 4) {
      // 100.64.0.0/10 — CGNAT
      return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
    }
    if (b.length == 16) {
      // fc00::/7 — IPv6 unique-local
      return (b[0] & 0xFE) == 0xFC;
    }
    return false;
  }

}
