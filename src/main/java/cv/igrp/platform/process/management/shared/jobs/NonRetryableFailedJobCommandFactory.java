package cv.igrp.platform.process.management.shared.jobs;

import org.activiti.engine.impl.cmd.JobRetryCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.jobexecutor.FailedJobCommandFactory;
import org.activiti.engine.impl.persistence.entity.JobEntity;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * What the engine runs when a job fails. A {@link NonRetryableJobException} anywhere in the cause goes
 * straight to the dead letter, with the message and stack trace JobRetryCmd would have set, so
 * /failed-jobs shows it after one attempt; everything else gets Activiti's own JobRetryCmd (retries,
 * failedJobRetryTimeCycle, then the dead letter). Set in AsyncJobRetryConfig.
 */
public class NonRetryableFailedJobCommandFactory implements FailedJobCommandFactory {

  @Override
  public Command<Object> getCommand(String jobId, Throwable exception) {
    if (!isNonRetryable(exception)) {
      return new JobRetryCmd(jobId, exception);
    }
    return commandContext -> {
      final JobEntity job = commandContext.getJobEntityManager().findById(jobId);
      if (job != null) {
        final var deadLetter = commandContext.getJobManager().moveJobToDeadLetterJob(job);
        deadLetter.setExceptionMessage(exception.getMessage());
        deadLetter.setExceptionStacktrace(stacktraceOf(exception));
      }
      return null;
    };
  }

  static boolean isNonRetryable(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof NonRetryableJobException) {
        return true;
      }
    }
    return false;
  }

  private static String stacktraceOf(Throwable exception) {
    final StringWriter out = new StringWriter();
    exception.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
