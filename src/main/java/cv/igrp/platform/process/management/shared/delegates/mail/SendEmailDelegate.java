package cv.igrp.platform.process.management.shared.delegates.mail;

import cv.igrp.platform.process.management.shared.util.EnvVarUtil;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component("igrpSendEmailDelegate")
public class SendEmailDelegate implements JavaDelegate {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailDelegate.class);

  private final JavaMailSender mailSender;
  private final String defaultFrom;

  public Expression emailTo;
  public Expression emailSubject;
  public Expression emailBody;
  public Expression emailFrom;

  public SendEmailDelegate(JavaMailSender mailSender,
                           @Value("${igrp.mail.default.from}") String defaultFrom) {
    this.mailSender = mailSender;
    this.defaultFrom = defaultFrom;
  }

  @Override
  public void execute(DelegateExecution execution) {
    LOGGER.info("Entered SendEmailDelegate");

    String toVariable = execution.getVariable("emailTo", String.class);
    String to = Objects.nonNull(toVariable) ? toVariable : Objects.nonNull(emailTo)? (String) emailTo.getValue(execution): null;
    String subjectVariable = execution.getVariable("emailSubject", String.class);
    String subject = Objects.nonNull(subjectVariable) ? subjectVariable : Objects.nonNull(emailSubject)? (String) emailSubject.getValue(execution): null;
    String bodyVariable = execution.getVariable("emailBody", String.class);
    String body = Objects.nonNull(bodyVariable) ? bodyVariable : Objects.nonNull(emailBody)? (String) emailBody.getValue(execution): null;
    String fromVariable = execution.getVariable("emailFrom", String.class);
    String from = Objects.nonNull(fromVariable) ? fromVariable : Objects.nonNull(emailFrom)? (String) emailFrom.getValue(execution): null;

    to = EnvVarUtil.resolveEnvVars(to, "emailTo");
    subject = EnvVarUtil.resolveEnvVars(subject, "emailSubject");
    body = EnvVarUtil.resolveEnvVars(body, "emailBody");
    from = EnvVarUtil.resolveEnvVars(from, "emailFrom");

    validate(to, subject, body);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(to);
    message.setFrom(from == null ? defaultFrom : from);
    message.setSubject(subject);
    message.setText(body);

    mailSender.send(message);
    LOGGER.info("Email successfully sent to: {}", to);
  }

  private void validate(String to, String subject, String body) {
    if (to == null || to.isBlank()) {
      throw new IllegalArgumentException("'emailTo' is required.");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("'emailSubject' is required.");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("'emailBody' is required.");
    }
  }

}
