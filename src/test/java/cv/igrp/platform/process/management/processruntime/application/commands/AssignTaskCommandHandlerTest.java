package cv.igrp.platform.process.management.processruntime.application.commands;

import cv.igrp.platform.process.management.processruntime.application.dto.AssignTaskDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskOperationData;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskInstanceService;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignTaskCommandHandlerTest {

  @Mock
  private TaskInstanceService taskInstanceService;

  @Mock
  private UserContext userContext;

  @InjectMocks
  private AssignTaskCommandHandler handler;

  private AssignTaskCommand command;

  @BeforeEach
  void setUp() {
    String taskId = UUID.randomUUID().toString();
    String currentUserName = "demo@nosi.cv";
    String targetUserName = "igrp@nosi.cv";
    String note = "This is a note";
    String candidateGroups = "group1,group2";
    String candidateUsers = "candidate1@nosi.cv,candidate2@nosi.cv";
    Integer priority = 3;

    when(userContext.getCurrentUser()).thenReturn(Code.create(currentUserName));

    AssignTaskDTO dto = new AssignTaskDTO(targetUserName, priority, note, candidateGroups, candidateUsers);
    command = new AssignTaskCommand();
    command.setId(taskId);
    command.setAssigntaskdto(dto);
  }

  @Test
  void handle_shouldInvokeServiceAndReturnNoContent() {
    // When
    ResponseEntity<String> response = handler.handle(command);

    // Then
    assertNotNull(response);
    assertEquals(204, response.getStatusCode().value());

    // verify
    verify(taskInstanceService, times(1)).assignTask(argThat(data ->
        data.getCandidateGroups().equals(List.of("group1", "group2"))
            && data.getCandidateUsers().equals(List.of("candidate1@nosi.cv", "candidate2@nosi.cv"))
    ));

    verifyNoMoreInteractions(taskInstanceService, userContext);
  }
}
