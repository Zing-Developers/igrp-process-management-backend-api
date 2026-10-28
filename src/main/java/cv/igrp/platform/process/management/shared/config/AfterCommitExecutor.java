package cv.igrp.platform.process.management.shared.config;

import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;

import java.util.concurrent.Executor;

/**
 * Where the job listeners run what follows a job's commit (see AsyncJobRetryConfig). Its own type on
 * purpose: a bean of type Executor would make Spring Boot drop its applicationTaskExecutor, and MVC
 * async requests and any @Async would end up on these two threads.
 */
public record AfterCommitExecutor(Executor delegate) {

  public void execute(Runnable task) {
    delegate.execute(task);
  }

  /** Lets queued work finish (bounded by the pool's await-termination) when the context closes. */
  public void shutdown() {
    if (delegate instanceof ExecutorConfigurationSupport pool) {
      pool.shutdown();
    }
  }
}
