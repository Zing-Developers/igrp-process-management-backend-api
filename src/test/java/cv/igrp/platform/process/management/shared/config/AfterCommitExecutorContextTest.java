package cv.igrp.platform.process.management.shared.config;

import cv.igrp.platform.process.management.shared.events.EngineJobSyncListener;
import cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The listeners' executor must not replace the applicationTaskExecutor Spring Boot sets up. */
class AfterCommitExecutorContextTest {

  @Test
  void bootKeepsItsTaskExecutorAndTheListenersGetTheirOwn() {
    new ApplicationContextRunner()
        // what a Boot application has: turns the "60s" default of igrp.async-job.retry-wait into a Duration
        .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
        .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
        .withUserConfiguration(AsyncJobRetryConfig.class, JobFailureRecorder.class, EngineJobSyncListener.class)
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasBean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME);
          assertThat(context.getBeansOfType(Executor.class)).containsOnlyKeys(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME);
          assertThat(context).hasSingleBean(AfterCommitExecutor.class);
        });
  }
}
