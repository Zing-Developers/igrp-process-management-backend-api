package cv.igrp.platform.process.management.shared.delegates.webhook;

import cv.igrp.platform.process.management.shared.delegates.outbound.OutboundRequestGuard;
import cv.igrp.platform.process.management.shared.util.MessageUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookUrlRequiredTest {

  private final RestClient restClient = mock(RestClient.class);
  private final OutboundRequestGuard guard = mock(OutboundRequestGuard.class);

  @Test
  void bothDelegatesNameTheMissingUrlInsteadOfFailingInsideUriParsing() {
    var plain = new IgrpWebhookDelegate(restClient, guard);
    var process = new IgrpProcessWebhookDelegate(mock(MessageUtil.class), restClient, guard);
    var blank = mock(Expression.class);

    for (JavaDelegate delegate : List.of(plain, process)) {
      // No variable, no BPMN field.
      assertRequired(delegate, mock(DelegateExecution.class));
    }

    // BPMN field present but resolving to blank or null.
    var execution = mock(DelegateExecution.class);
    when(blank.getValue(execution)).thenReturn("  ");
    plain.webhookUrl = blank;
    process.webhookUrl = blank;
    assertRequired(plain, execution);
    assertRequired(process, execution);

    when(blank.getValue(execution)).thenReturn(null);
    assertRequired(plain, execution);
    assertRequired(process, execution);
  }

  @Test
  void inAnAsyncTaskAConfigurationErrorSkipsTheRetriesButADnsFailureKeepsThem() {
    var plain = new IgrpWebhookDelegate(restClient, guard);
    var process = new IgrpProcessWebhookDelegate(mock(MessageUtil.class), restClient, guard);
    var asyncTask = new org.activiti.bpmn.model.ServiceTask();
    asyncTask.setAsynchronous(true);
    var execution = mock(DelegateExecution.class);
    when(execution.getCurrentActivityId()).thenReturn("notify");
    when(execution.getCurrentFlowElement()).thenReturn(asyncTask);

    for (JavaDelegate delegate : List.of(plain, process)) {
      var e = assertThrows(cv.igrp.platform.process.management.shared.jobs.NonRetryableJobException.class, () -> delegate.execute(execution));
      assertEquals("notify: webhookUrl argument is required and was not provided", e.getMessage());
    }

    // the host is configured but DNS does not answer right now: left to the engine's retries
    var url = mock(Expression.class);
    when(url.getValue(execution)).thenReturn("https://irn.example");
    plain.webhookUrl = url;
    process.webhookUrl = url;
    when(guard.validate(org.mockito.ArgumentMatchers.anyString())).thenThrow(new OutboundRequestGuard.UnresolvableHostException());
    for (JavaDelegate delegate : List.of(plain, process)) {
      assertThrows(OutboundRequestGuard.UnresolvableHostException.class, () -> delegate.execute(execution));
    }
  }

  private static void assertRequired(JavaDelegate delegate, DelegateExecution execution) {
    var e = assertThrows(IllegalArgumentException.class, () -> delegate.execute(execution));
    assertEquals("webhookUrl argument is required and was not provided", e.getMessage());
  }
}
