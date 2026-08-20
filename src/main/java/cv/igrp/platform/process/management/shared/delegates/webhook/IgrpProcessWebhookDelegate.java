package cv.igrp.platform.process.management.shared.delegates.webhook;

import cv.igrp.platform.process.management.shared.delegates.outbound.OutboundRequestGuard;

import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import cv.igrp.platform.process.management.shared.util.MessageUtil;
import cv.igrp.platform.process.management.shared.util.ObjectUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

import static java.util.Optional.ofNullable;

@Component("igrpProcessWebhookDelegate")
public class IgrpProcessWebhookDelegate implements JavaDelegate {

  private final MessageUtil messageUtil;

  private static final Logger log = LoggerFactory.getLogger(IgrpProcessWebhookDelegate.class);

  private final RestClient restClient;

  @Value(value = "${igrp.delegate.webhook.auth-token:}")
  private String globalAuthToken;

  public Expression webhookUrl;
  public Expression webhookUrlPath;
  public Expression webhookPayloadHeader;

  private final OutboundRequestGuard guard;

  public IgrpProcessWebhookDelegate(MessageUtil messageUtil, RestClient restClient, OutboundRequestGuard guard) {
    this.messageUtil = messageUtil;
    this.restClient = restClient;
    this.guard = guard;
  }

  @Override
  public void execute(DelegateExecution execution) {

    String taskId = execution.getCurrentActivityId();
    String processInstanceId = execution.getProcessInstanceId();
    log.debug("[IgrpProcessWebhookDelegate] Executing webhook task: {} from process instance: {}", taskId, processInstanceId);
    String baseUrlVariable = (String) execution.getVariable("webhookUrl");
    String baseUrl = Objects.nonNull(baseUrlVariable)? baseUrlVariable: Objects.nonNull(webhookUrl)? webhookUrl.getValue(execution).toString() : null;
    baseUrl = EnvVarUtil.resolveEnvVars(baseUrl, "webhookUrl");
    String pathVariable = (String)  execution.getVariable("webhookUrlPath");
    String path = Objects.nonNull(pathVariable) ?  pathVariable : Objects.nonNull(webhookUrlPath) ? (String) webhookUrlPath.getValue(execution): null;
    path = EnvVarUtil.resolveEnvVars(path, "webhookUrlPath");

    String url = guard.validate(UriComponentsBuilder.fromUriString(baseUrl)
        .path(path != null ? path : "")
        .build()
        .toUriString());

    String payload = messageUtil.createMessage(execution);

    Object payloadHeader = execution.getVariable("webhookPayloadHeader");
    String payloadHeaderStr = ofNullable(Objects.nonNull(payloadHeader)? payloadHeader : Objects.nonNull(webhookPayloadHeader) ? webhookPayloadHeader.getValue(execution) : null)
        .orElse("").toString();
    payloadHeaderStr = EnvVarUtil.resolveEnvVars(payloadHeaderStr, "webhookPayloadHeader");
    Map<String, String> headersMap = ObjectUtil.parseJsonObjectString(payloadHeaderStr);

    try {
      HttpHeaders headers = guard.buildHeaders(headersMap, globalAuthToken);

      log.debug("[IgrpProcessWebhookDelegate] Sending request to {}", url);
      log.debug("[IgrpProcessWebhookDelegate] Payload: {}", payload);

      var response = restClient.post()
          .uri(url)
          .headers(httpHeaders -> httpHeaders.addAll(headers))
          .body(payload)
          .exchange((request, clientResponse) -> guard.readBounded(clientResponse));

      if (response.status() >= 400) {
        log.warn("[IgrpProcessWebhookDelegate] Webhook returned error {}", response.status());
      } else {
        log.info("[IgrpProcessWebhookDelegate] Process Data successfully sent to webhook");
      }
    } catch (Exception e) {
      log.error("[IgrpProcessWebhookDelegate] Error calling webhook {}", url, e);
    }
  }

}
