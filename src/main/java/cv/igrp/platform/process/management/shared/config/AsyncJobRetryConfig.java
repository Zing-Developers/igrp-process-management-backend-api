package cv.igrp.platform.process.management.shared.config;

import cv.igrp.platform.process.management.shared.events.EngineJobSyncListener;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  /** In the engine configuration, so the sync listener is there before the job executor starts. */
  @Bean
  public ProcessEngineConfigurationConfigurer engineJobSyncConfigurer(EngineJobSyncListener listener) {
    return conf -> {
      final Map<String, List<ActivitiEventListener>> typed = conf.getTypedEventListeners() == null
          ? new HashMap<>() : new HashMap<>(conf.getTypedEventListeners());
      typed.computeIfAbsent(ActivitiEventType.JOB_EXECUTION_SUCCESS.name(), type -> new ArrayList<>()).add(listener);
      conf.setTypedEventListeners(typed);
    };
  }

}
