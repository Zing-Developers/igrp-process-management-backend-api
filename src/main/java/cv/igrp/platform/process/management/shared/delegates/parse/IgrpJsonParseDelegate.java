package cv.igrp.platform.process.management.shared.delegates.parse;

import com.google.gson.JsonElement;
import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import cv.igrp.platform.process.management.shared.util.ObjectUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component("igrpJsonParseDelegate")
public class IgrpJsonParseDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(IgrpJsonParseDelegate.class);

  public Expression json;
  public Expression isBase64Encoded;

  @Override
  public void execute(DelegateExecution execution) {

    String taskId = execution.getCurrentActivityId();
    String processInstanceId = execution.getProcessInstanceId();
    log.debug("[IgrpJsonParseDelegate] Executing parse task: {} from process instance: {}", taskId, processInstanceId);
    String jsonVariable = (String) execution.getVariable("json");
    String payload = Objects.nonNull(jsonVariable)? jsonVariable: Objects.nonNull(json)? json.getValue(execution).toString() : null;
    payload = EnvVarUtil.resolveEnvVars(payload, "json");
    String isBase64Variable = (String) execution.getVariable("isBase64Encoded");
    String isBase64Raw = Objects.nonNull(isBase64Variable) ? isBase64Variable : Objects.nonNull(isBase64Encoded) ? (String) isBase64Encoded.getValue(execution) : null;
    isBase64Raw = EnvVarUtil.resolveEnvVars(isBase64Raw, "isBase64Encoded");
    boolean isBase64 = Boolean.parseBoolean(isBase64Raw);

    if (isBase64) {
      payload = ObjectUtil.decodeBase64ToString(payload);
    }

    try {

      JsonElement payloadElement = ObjectUtil.parseJsonObject(payload);
      Object payloadParsed = ObjectUtil.toJavaObject(payloadElement);

      execution.getEngineServices().getRuntimeService().setVariable(
          processInstanceId,taskId + "Data", payloadParsed);

      log.debug("[IgrpJsonParseDelegate] Data parsed successfully");

    } catch (Exception e) {
      log.error("[IgrpJsonParseDelegate] Error parsing JSON: ", e);
      execution.setTransientVariable(taskId + "Error", e.getMessage());
    }

  }

}
