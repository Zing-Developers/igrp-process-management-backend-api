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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnClaimTaskCommandHandlerTest {

  @Mock
  private TaskInstanceService taskInstanceService;

  @Mock
  private UserContext userContext;

  @InjectMocks
  private UnClaimTaskCommandHandler handler;

  @InjectMocks
  private UnClaimTaskCommandHandler unClaimTaskCommandHandler;

  private UnClaimTaskCommand command;



  @BeforeEach
  void setUp() {

    when(userContext.getCurrentUser()).thenReturn(Code.create("demo@nosi.cv"));

    command = new UnClaimTaskCommand();
    command.setId(UUID.randomUUID().toString());
  }


  @Test
  void handle_shouldCallClaimTaskServiceAndReturnNoContent() {
    // When
    ResponseEntity<String> response = handler.handle(command);

    // Then
    assertNotNull(response);
    assertEquals(204, response.getStatusCode().value());

    // Verify
    verify(taskInstanceService, times(1)).unClaimTask(any(TaskOperationData.class));

    verifyNoMoreInteractions(taskInstanceService, userContext);
  }
}
