package cv.igrp.platform.process.management.shared.delegates.webhook;

import org.activiti.bpmn.model.FlowNode;
import org.activiti.engine.delegate.DelegateExecution;

/**
 * Failure policy shared by the webhook delegates.
 *
 * <p>Every outcome is recorded as persistent process variables ({@code <taskId>StatusCode},
 * {@code <taskId>Error}) so the BPMN and the console can see it. A retryable failure (no response,
 * 5xx, 408, 429) on a service task marked {@code activiti:async="true"} is rethrown instead: the job
 * executor retries it ({@code failedJobRetryTimeCycle} on the task, or the IGRP_ASYNC_JOB_* defaults)
 * and, once retries run out, parks it as a dead-letter job that /failed-jobs can reprocess.
 * Synchronous tasks keep the old behaviour and continue, so a down webhook never fails the user
 * action that triggered it.
 */
final class WebhookFailures {

  private WebhookFailures() {
  }

  static boolean isRetryable(int status) {
    return status >= 500 || status == 408 || status == 429;
  }

  static boolean isAsync(DelegateExecution execution) {
    return execution.getCurrentFlowElement() instanceof FlowNode node && node.isAsynchronous();
  }

  /**
   * Records the outcome of a call: {@code status} when the endpoint answered, {@code cause} when it did
   * not. Throws when the job executor should retry it.
   */
  static void record(DelegateExecution execution, String taskId, Integer status, Exception cause) {
    final String error = cause != null
        ? (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName())
        : (status != null && status >= 400 ? "HTTP " + status : null);
    if (error != null && isAsync(execution) && (status == null || isRetryable(status))) {
      // Rolled back with the job; the message lands on the (dead-letter) job instead
      throw new WebhookCallException(taskId + ": " + error, cause);
    }
    var runtime = execution.getEngineServices().getRuntimeService();
    var processInstanceId = execution.getProcessInstanceId();
    if (status != null) {
      runtime.setVariable(processInstanceId, taskId + "StatusCode", status);
    }
    if (error != null) {
      runtime.setVariable(processInstanceId, taskId + "Error", error);
    } else {
      runtime.removeVariable(processInstanceId, taskId + "Error");
    }
  }

  static final class WebhookCallException extends RuntimeException {
    WebhookCallException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
