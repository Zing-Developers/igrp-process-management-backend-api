package cv.igrp.platform.process.management.shared.security.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfiguration {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String url;

  /**
   * Defines a default JwtDecoder bean if one is not already configured.
   */
  /**
   * Fails startup when the OIDC provider is unreachable. The previous catch-all returned a decoder
   * that rejected every token until restart — a silent, unalertable outage
   * (SECURITY_RECOMMENDATIONS P0). Readiness probes handle the not-ready window.
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withIssuerLocation(url).build();
  }

}
