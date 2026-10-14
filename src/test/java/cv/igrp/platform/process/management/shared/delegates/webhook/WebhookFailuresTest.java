package cv.igrp.platform.process.management.shared.delegates.webhook;

import org.activiti.bpmn.model.ServiceTask;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookFailuresTest {

  private final DelegateExecution execution = mock(DelegateExecution.class);
  private final RuntimeService runtime = mock(RuntimeService.class);
  private final ServiceTask task = new ServiceTask();

  @BeforeEach
  void setUp() {
    var services = mock(ProcessEngineConfiguration.class);
    when(services.getRuntimeService()).thenReturn(runtime);
    when(execution.getEngineServices()).thenReturn(services);
    when(execution.getProcessInstanceId()).thenReturn("pi-1");
    when(execution.getCurrentFlowElement()).thenReturn(task);
  }

  @Test
  void asyncTaskRethrowsRetryableFailuresSoTheJobExecutorRetries() {
    task.setAsynchronous(true);
    assertThrows(WebhookFailures.WebhookCallException.class,
        () -> WebhookFailures.record(execution, "notify", null, "timeout", new IOException("timeout")));
    assertThrows(WebhookFailures.WebhookCallException.class,
        () -> WebhookFailures.record(execution, "notify", 503, "HTTP 503", null));
    assertThrows(WebhookFailures.WebhookCallException.class,
        () -> WebhookFailures.record(execution, "notify", 429, "HTTP 429", null));
    verify(runtime, never()).setVariable(anyString(), anyString(), any());
  }

  @Test
  void asyncTaskRecordsAndContinuesOnADefinitive4xx() {
    task.setAsynchronous(true);
    WebhookFailures.record(execution, "notify", 404, "HTTP 404", null);
    verify(runtime).setVariable("pi-1", "notifyStatusCode", 404);
    verify(runtime).setVariable("pi-1", "notifyError", "HTTP 404");
  }

  @Test
  void syncTaskNeverThrowsAndKeepsTheErrorAsAPersistentVariable() {
    WebhookFailures.record(execution, "notify", null, "connection refused", new IOException());
    verify(runtime).setVariable("pi-1", "notifyError", "connection refused");
  }

  @Test
  void successClearsAStaleError() {
    WebhookFailures.record(execution, "notify", 200, null, null);
    verify(runtime).setVariable("pi-1", "notifyStatusCode", 200);
    verify(runtime).removeVariable("pi-1", "notifyError");
  }

}
