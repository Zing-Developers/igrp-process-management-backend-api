package cv.igrp.platform.process.management.shared.config;

import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Engine-wide retry policy for failed async jobs (async service tasks such as webhooks marked
 * {@code activiti:async="true"}). A task's own {@code failedJobRetryTimeCycle} (e.g. R5/PT10M) wins
 * over these. When retries run out the job becomes a dead-letter job, listed and reprocessed via
 * /failed-jobs.
 */
@Configuration
public class AsyncJobRetryConfig {

  @Bean
  public ProcessEngineConfigurationConfigurer asyncJobRetryConfigurer(
      @Value("${igrp.async-job.retries:3}") int retries,
      @Value("${igrp.async-job.retry-wait:60s}") Duration retryWait) {
    final int waitSeconds = (int) retryWait.toSeconds();
    return conf -> {
      conf.setAsyncExecutorNumberOfRetries(retries);
      conf.setAsyncFailedJobWaitTime(waitSeconds);
      conf.setDefaultFailedJobWaitTime(waitSeconds);
    };
  }

}
