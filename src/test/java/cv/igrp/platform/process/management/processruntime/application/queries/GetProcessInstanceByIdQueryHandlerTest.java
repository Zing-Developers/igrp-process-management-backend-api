package cv.igrp.platform.process.management.processruntime.application.queries;

import cv.igrp.platform.process.management.processruntime.application.dto.ProcessInstanceDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.processruntime.mappers.ProcessInstanceMapper;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetProcessInstanceByIdQueryHandlerTest {

  @Mock
  private ProcessInstanceService processInstanceService;

  @Mock
  private ProcessInstanceMapper processInstanceMapper;

  @Mock
  private UserContext userContext;

  @InjectMocks
  private GetProcessInstanceByIdQueryHandler handler;

  private GetProcessInstanceByIdQuery query;
  private String processInstanceId;

  private ProcessInstance mockProcessInstance;
  private ProcessInstanceDTO mockProcessInstanceDTO;


  @BeforeEach
  void setUp() {

    processInstanceId = UUID.randomUUID().toString();

    when(userContext.getCurrentUser()).thenReturn(Code.create("demo@nosi.cv"));

    // Mock ProcessInstance and ProcessInstanceDTO
    mockProcessInstance = mock(ProcessInstance.class);
    mockProcessInstanceDTO = new ProcessInstanceDTO();

    when(mockProcessInstance.getId()).thenReturn(Identifier.create(processInstanceId));

    when(processInstanceService.getProcessInstanceById(UUID.fromString(processInstanceId)))
        .thenReturn(mockProcessInstance);
    when(processInstanceMapper.toDTO(mockProcessInstance))
        .thenReturn(mockProcessInstanceDTO);

    query = new GetProcessInstanceByIdQuery();
    query.setId(processInstanceId);
  }

  @Test
  void handle_shouldReturnOkWithProcessInstanceDto() {
    // When
    ResponseEntity<ProcessInstanceDTO> response = handler.handle(query);

    // Then
    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertSame(mockProcessInstanceDTO, response.getBody());

    verify(processInstanceService).getProcessInstanceById(UUID.fromString(processInstanceId));
    verify(processInstanceMapper).toDTO(mockProcessInstance);
    verifyNoMoreInteractions(processInstanceService, processInstanceMapper, userContext);
  }
}
