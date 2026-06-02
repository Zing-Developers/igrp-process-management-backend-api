package cv.igrp.platform.process.management.shared.delegates.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleRequest;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbstractProcessEventConsumerTest {

  @Mock
  private ProcessInstanceService processInstanceService;

  @Mock
  private JwtDecoder jwtDecoder;

  @Test
  void handleMessage_shouldPassAssignmentRulesToSignal() {
    var consumer = new TestProcessEventConsumer(processInstanceService, new ObjectMapper(), jwtDecoder);
    String payload = """
        {
          "businessKey": "BUS-1",
          "taskId": "task-1",
          "variables": { "approved": true },
          "assignmentRules": [
            {
              "taskKey": "next-task",
              "assignee": "owner@nosi.cv",
              "candidateUsers": "user1@nosi.cv, user2@nosi.cv",
              "assignmentMode": "ALWAYS",
              "priority": 7
            }
          ]
        }
        """;

    consumer.handleMessage(new ConsumerRecord<>("topic", 0, 0L, "key", payload));

    ArgumentCaptor<List<TaskAssignmentRuleRequest>> rulesCaptor = ArgumentCaptor.forClass(List.class);
    verify(processInstanceService).signal(
        eq("BUS-1"),
        eq("task-1"),
        eq(Map.of("approved", true)),
        rulesCaptor.capture()
    );
    var rule = rulesCaptor.getValue().get(0);
    assertEquals("next-task", rule.getTaskKey().getValue());
    assertEquals("owner@nosi.cv", rule.getAssignee().getValue());
    assertTrue(rule.getCandidateUsers().containsAll(List.of("user1@nosi.cv", "user2@nosi.cv")));
    assertEquals(TaskAssignmentMode.ALWAYS, rule.getAssignmentMode());
    assertEquals(7, rule.getPriority());
  }

  @Test
  void handleMessage_shouldPassAssignmentRulesToCorrelateMessage() {
    var consumer = new TestProcessEventConsumer(processInstanceService, new ObjectMapper(), jwtDecoder);
    String payload = """
        {
          "businessKey": "BUS-1",
          "messageName": "message-a",
          "variables": { "approved": true },
          "assignmentRules": [
            {
              "taskKey": "next-task",
              "candidateUsers": "user1@nosi.cv",
              "assignmentMode": "ONE_TIME"
            }
          ]
        }
        """;

    consumer.handleMessage(new ConsumerRecord<>("topic", 0, 0L, "key", payload));

    ArgumentCaptor<List<TaskAssignmentRuleRequest>> rulesCaptor = ArgumentCaptor.forClass(List.class);
    verify(processInstanceService).correlateMessage(
        eq("BUS-1"),
        eq("message-a"),
        eq(Map.of("approved", true)),
        rulesCaptor.capture()
    );
    var rule = rulesCaptor.getValue().get(0);
    assertEquals("next-task", rule.getTaskKey().getValue());
    assertEquals(List.of("user1@nosi.cv"), rule.getCandidateUsers());
    assertEquals(TaskAssignmentMode.ONE_TIME, rule.getAssignmentMode());
  }

  private static class TestProcessEventConsumer extends AbstractProcessEventConsumer {

    protected TestProcessEventConsumer(
        ProcessInstanceService processInstanceService,
        ObjectMapper objectMapper,
        JwtDecoder jwtDecoder
    ) {
      super(processInstanceService, objectMapper, jwtDecoder);
    }
  }
}
