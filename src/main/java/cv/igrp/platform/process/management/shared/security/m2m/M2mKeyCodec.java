package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes M2M API keys.
 *
 * <p>Keys are {@code igrpm2m_<32 random bytes base64url>}. At rest only the HMAC-SHA-256 keyed with a
 * server-side pepper is stored, so a dumped table is useless without the application secret. The
 * pepper comes from {@code IGRP_M2M_KEY_PEPPER}; production must set it (the empty default keeps
 * development friction-free but offers plain-SHA-256-equivalent protection only).
 */
@Component
public class M2mKeyCodec {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int KEY_BYTES = 32;

  private final byte[] pepper;

  public M2mKeyCodec(@Value("${igrp.authorization.m2m.pepper:}") String pepper) {
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  /** A new plaintext key — shown once, never persisted. */
  public String newKey() {
    byte[] bytes = new byte[KEY_BYTES];
    RANDOM.nextBytes(bytes);
    return M2mKeyResolver.KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** HMAC-SHA-256 of the raw key, hex-encoded — the only form that touches the database. */
  public String hash(String rawKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      // an HMAC key must be non-empty; a single zero byte keys it deterministically when no pepper is set
      mac.init(new SecretKeySpec(pepper.length > 0 ? pepper : new byte[]{0}, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  /** First printable chars of a key (prefix + 4) — safe to store and log for identification. */
  public String prefixOf(String rawKey) {
    return rawKey.substring(0, Math.min(rawKey.length(), M2mKeyResolver.KEY_PREFIX.length() + 4));
  }

}
