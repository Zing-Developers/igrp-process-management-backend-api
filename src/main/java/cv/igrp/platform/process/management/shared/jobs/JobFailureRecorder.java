package cv.igrp.platform.process.management.shared.jobs;

import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.ActivitiExceptionEvent;
import org.activiti.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Records when and how often an engine job failed (t_job_failure, V15), which Activiti does not keep,
 * for the failed-jobs console. Failure upserts the row of the job's execution; success clears it.
 * Writes run in a transaction of their own: a failure is dispatched inside the failing job's
 * transaction, which rolls back.
 */
@Component
public class JobFailureRecorder implements ActivitiEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobFailureRecorder.class);
  private static final int MAX_ERROR = 4000;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate newTransaction;

  public JobFailureRecorder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public record Failure(int attempts, LocalDateTime firstFailedAt, LocalDateTime lastFailedAt) {
  }

  @Override
  public void onEvent(ActivitiEvent event) {
    if (!(event instanceof ActivitiEntityEvent entityEvent) || !(entityEvent.getEntity() instanceof Job job)
        || job.getExecutionId() == null) {
      return;
    }
    try {
      if (event.getType() == ActivitiEventType.JOB_EXECUTION_FAILURE) {
        final Throwable cause = event instanceof ActivitiExceptionEvent failure ? failure.getCause() : null;
        recordFailure(job.getExecutionId(), job.getProcessInstanceId(), messageOf(cause));
      } else if (event.getType() == ActivitiEventType.JOB_EXECUTION_SUCCESS) {
        newTransaction.executeWithoutResult(s -> jdbc.update("DELETE FROM t_job_failure WHERE execution_id = ?", job.getExecutionId()));
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Could not record the outcome of job {} (execution {})", job.getId(), job.getExecutionId(), e);
    }
  }

  void recordFailure(String executionId, String processInstanceId, String error) {
    final Timestamp now = Timestamp.from(Instant.now());
    final String message = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR));
    newTransaction.executeWithoutResult(s -> {
      // one job runs per execution at a time, so update-then-insert does not race
      final int updated = jdbc.update(
          "UPDATE t_job_failure SET attempts = attempts + 1, last_failed_at = ?, last_error = ? WHERE execution_id = ?",
          now, message, executionId);
      if (updated == 0) {
        jdbc.update("INSERT INTO t_job_failure (execution_id, process_instance_id, attempts, first_failed_at, last_failed_at, last_error)"
            + " VALUES (?, ?, 1, ?, ?, ?)", executionId, processInstanceId, now, now, message);
      }
    });
  }

  public Map<String, Failure> findByExecutionIds(Collection<String> executionIds) {
    final Map<String, Failure> found = new HashMap<>();
    if (executionIds.isEmpty()) {
      return found;
    }
    final String marks = String.join(",", executionIds.stream().map(id -> "?").toList());
    jdbc.query("SELECT execution_id, attempts, first_failed_at, last_failed_at FROM t_job_failure WHERE execution_id IN (" + marks + ")",
        rs -> {
          found.put(rs.getString(1), new Failure(rs.getInt(2),
              rs.getTimestamp(3).toLocalDateTime(), rs.getTimestamp(4).toLocalDateTime()));
        },
        executionIds.toArray());
    return found;
  }

  private static String messageOf(Throwable cause) {
    Throwable root = cause;
    while (root != null && root.getCause() != null && root.getMessage() != null && root.getMessage().startsWith("Job ")) {
      root = root.getCause(); // "Job 7 failed" wraps the delegate's own exception
    }
    return root == null ? null : root.getMessage();
  }

  @Override
  public boolean isFailOnException() {
    return false;
  }

}
