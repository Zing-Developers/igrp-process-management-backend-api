package cv.igrp.platform.process.management.processruntime.application.commands;

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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimTaskCommandHandlerTest {

  @Mock
  private TaskInstanceService taskInstanceService;

  @Mock
  private UserContext userContext;

  @InjectMocks
  private ClaimTaskCommandHandler handler;

  private ClaimTaskCommand command;

  @BeforeEach
  void setUp() {
    String taskId = UUID.randomUUID().toString();
    String currentUserName = "demo@nosi.cv";

    when(userContext.getCurrentUser()).thenReturn(Code.create(currentUserName));

    command = new ClaimTaskCommand();
    command.setId(taskId);
  }

  @Test
  void handle_shouldInvokeServiceAndReturnNoContent() {
    // When
    ResponseEntity<String> response = handler.handle(command);

    // Then
    assertNotNull(response);
    assertEquals(204, response.getStatusCode().value());

    // Verify
    verify(taskInstanceService, times(1)).claimTask(any(TaskOperationData.class));

    verifyNoMoreInteractions(taskInstanceService, userContext);
  }
}
