package cv.igrp.platform.process.management.shared.config;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.spring.boot.ProcessEngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The engine starts its job executor the moment it is built, halfway through the context's startup.
 * Activiti's ProcessExtensionService gets its RepositoryService later, from a bean of its own, so a job
 * due at boot that reaches a user task fails with a NullPointerException and spends a retry (seen on a
 * deploy: two failures, done on the third). The executor starts here instead, once the application is
 * ready. Jobs created in between wait in the table.
 * <p>
 * spring.activiti.async-executor-activate=false still turns the executor off: then it never starts.
 */
@Configuration
public class JobExecutorStartConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutorStartConfig.class);

  /** Runs after Boot copied spring.activiti.async-executor-activate, so this has the last word. */
  @Bean
  public ProcessEngineConfigurationConfigurer deferJobExecutorConfigurer() {
    return conf -> conf.setAsyncExecutorActivate(false);
  }

  @Bean
  public ApplicationListener<ApplicationReadyEvent> jobExecutorStarter(
      ObjectProvider<ProcessEngine> processEngine,
      @Value("${spring.activiti.async-executor-activate:true}") boolean activate) {
    return event -> {
      if (activate) {
        processEngine.ifAvailable(JobExecutorStartConfig::startJobExecutor);
      }
    };
  }

  static void startJobExecutor(ProcessEngine processEngine) {
    var asyncExecutor = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAsyncExecutor();
    if (asyncExecutor != null && !asyncExecutor.isActive()) {
      LOGGER.info("Application ready: starting the engine's job executor");
      asyncExecutor.start();
    }
  }
}
