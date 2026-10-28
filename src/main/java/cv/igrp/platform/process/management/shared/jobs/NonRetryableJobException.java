package cv.igrp.platform.process.management.shared.jobs;

/**
 * Thrown by a delegate in an async task when running it again cannot succeed (a missing URL, a host the
 * outbound policy blocks): {@link NonRetryableFailedJobCommandFactory} sends the job straight to the
 * dead letter, with this message, instead of spending its retries.
 */
public class NonRetryableJobException extends RuntimeException {

  public NonRetryableJobException(String message, Throwable cause) {
    super(message, cause);
  }
}
