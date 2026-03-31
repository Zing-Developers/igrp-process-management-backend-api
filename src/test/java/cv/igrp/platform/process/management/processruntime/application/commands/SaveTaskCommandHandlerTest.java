package cv.igrp.platform.process.management.processruntime.application.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cv.igrp.platform.process.management.processruntime.application.dto.ProcessVariableDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskDataDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskVariableDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskOperationData;
import cv.igrp.platform.process.management.processruntime.domain.service.TaskInstanceService;
import cv.igrp.platform.process.management.processruntime.mappers.TaskInstanceMapper;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
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

@ExtendWith(MockitoExtension.class)
public class SaveTaskCommandHandlerTest {

  @Mock
  private TaskInstanceService taskInstanceService;

  @Mock
  private TaskInstanceMapper taskInstanceMapper;

  @Mock
  private UserContext userContext;

  @InjectMocks
  private SaveTaskCommandHandler handler;

  private SaveTaskCommand command;

  private TaskInstance mockTaskInstance;
  private Code mockCodeInstance;
  private TaskInstanceDTO mockTaskInstanceDTO;

  @BeforeEach
  void setUp() {

    var id = UUID.randomUUID().toString();

    when(userContext.getCurrentUser()).thenReturn(Code.create("demo@nosi.cv"));

    // Mock TaskInstance and TaskInstanceDTO
    mockTaskInstance = mock(TaskInstance.class);
    mockTaskInstanceDTO = mock(TaskInstanceDTO.class);

    when(taskInstanceService.saveTask(any(TaskOperationData.class)))
        .thenReturn(mockTaskInstance);
    when(taskInstanceMapper.toTaskInstanceDTO(mockTaskInstance))
        .thenReturn(mockTaskInstanceDTO);

    // Command
    TaskDataDTO taskDataDTO = new TaskDataDTO();
    taskDataDTO.setForms(List.of(new TaskVariableDTO("form1", "value1")));
    taskDataDTO.setVariables(List.of(new ProcessVariableDTO("var1", "value2")));

    command = new SaveTaskCommand();
    command.setId(id);
    command.setTaskdatadto(taskDataDTO);
  }

  @Test
  void handle_shouldCompleteTaskAndReturnOkWithDto() {
    // When
    ResponseEntity<TaskInstanceDTO> response = handler.handle(command);

    // Then
    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertSame(mockTaskInstanceDTO, response.getBody());

    // Verify
    verify(taskInstanceService, times(1))
        .saveTask(any(TaskOperationData.class));
    verify(taskInstanceMapper, times(1))
        .toTaskInstanceDTO(mockTaskInstance);
    verifyNoMoreInteractions(taskInstanceService, taskInstanceMapper, userContext);
  }
}
