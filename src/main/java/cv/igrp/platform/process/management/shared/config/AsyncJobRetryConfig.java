package cv.igrp.platform.process.management.shared.config;

import cv.igrp.platform.process.management.shared.events.EngineJobSyncListener;
import cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder;
import cv.igrp.platform.process.management.shared.jobs.NonRetryableFailedJobCommandFactory;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
      // a configuration error (missing URL, blocked host) goes to the dead letter without retries
      conf.setFailedJobCommandFactory(new NonRetryableFailedJobCommandFactory());
    };
  }

  /**
   * Runs what the job listeners do once a job commits (sync the app, clear a failure record) off the
   * job thread. In afterCommit the job's connection is still checked out, so doing that work there,
   * in a transaction of its own, held two connections per job thread: twice the executor's threads
   * against the pool. Handed over, the job thread returns its connection at once, and these two
   * threads add at most two.
   */
  @Bean(destroyMethod = "shutdown")
  public AfterCommitExecutor engineAfterCommitExecutor() {
    final var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setThreadNamePrefix("engine-after-commit-");
    // a sync left in the queue would wait for TaskReconciler; let the queue drain on shutdown
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.initialize();
    return new AfterCommitExecutor(executor);
  }

  /** In the engine configuration, so these listeners are there before the job executor starts. */
  @Bean
  public ProcessEngineConfigurationConfigurer engineJobSyncConfigurer(EngineJobSyncListener listener,
                                                                     JobFailureRecorder failures) {
    return conf -> {
      final Map<String, List<ActivitiEventListener>> typed = conf.getTypedEventListeners() == null
          ? new HashMap<>() : new HashMap<>(conf.getTypedEventListeners());
      typed.computeIfAbsent(ActivitiEventType.JOB_EXECUTION_SUCCESS.name(), type -> new ArrayList<>()).addAll(List.of(listener, failures));
      typed.computeIfAbsent(ActivitiEventType.JOB_EXECUTION_FAILURE.name(), type -> new ArrayList<>()).add(failures);
      conf.setTypedEventListeners(typed);
    };
  }

}
