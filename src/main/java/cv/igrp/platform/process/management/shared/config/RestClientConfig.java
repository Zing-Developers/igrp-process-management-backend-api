package cv.igrp.platform.process.management.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

  // Fallback RestClient, used only when no other RestClient bean exists (e.g. when the
  // framework's signed-authorization client is inactive because igrp.restclient.provider != irn).
  // Bounds the previously infinite connect/read timeouts so a slow downstream service cannot
  // hold a process-engine worker thread (and its DB connection) indefinitely.
  @Bean
  @ConditionalOnMissingBean(RestClient.class)
  public RestClient defaultRestClient(
      RestClient.Builder builder,
      @Value("${igrp.restclient.connect-timeout:5s}") Duration connectTimeout,
      @Value("${igrp.restclient.read-timeout:10s}") Duration readTimeout) {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(connectTimeout)
        .withReadTimeout(readTimeout);
    return builder
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }

}
