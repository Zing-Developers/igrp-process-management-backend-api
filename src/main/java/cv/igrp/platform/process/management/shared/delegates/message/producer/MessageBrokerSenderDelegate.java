package cv.igrp.platform.process.management.shared.delegates.message.producer;

import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import cv.igrp.platform.process.management.shared.util.MessageUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component("igrpMessageBrokerSenderDelegate")
@ConditionalOnBean(MessageBrokerSender.class)
public class MessageBrokerSenderDelegate implements JavaDelegate {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageBrokerSenderDelegate.class);

  private final MessageBrokerSender messageSender;
  private final MessageUtil messageUtil;

  public Expression topic;

  public MessageBrokerSenderDelegate(MessageBrokerSender messageSender,
                                     MessageUtil messageUtil
  ) {
    this.messageSender = messageSender;
    this.messageUtil = messageUtil;
  }

  @Override
  public void execute(DelegateExecution execution) {
    LOGGER.info("Entered MessageSenderDelegate");

    if (topic == null) {
      throw new IllegalArgumentException("'topic' which represent topic or queue is required.");
    }

    String topicValue = topic.getValue(execution).toString();
    topicValue = EnvVarUtil.resolveEnvVars(topicValue, "topic");

    var message = messageUtil.createMessage(execution);

    LOGGER.info("Sending message...: \n{}\n", message);

    messageSender.send(topicValue, message);

    LOGGER.info("Message successfully sent to: {}", topicValue);

  }

}
