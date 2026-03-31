package cv.igrp.platform.process.management.area.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.process.management.area.application.dto.AreaDTO;
import cv.igrp.platform.process.management.area.application.dto.AreaRequestDTO;
import cv.igrp.platform.process.management.area.domain.models.Area;
import cv.igrp.platform.process.management.area.domain.service.AreaService;
import cv.igrp.platform.process.management.area.mappers.AreaMapper;
import cv.igrp.platform.process.management.area.mappers.AreaProcessMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
public class CreateAreaCommandHandlerTest {

  @Mock
  private AreaService areaService;

  private CreateAreaCommandHandler handler;

  private AreaMapper areaMapper;

  @BeforeEach
  public void setup() {
    areaMapper = new AreaMapper(new AreaProcessMapper());
    handler = new CreateAreaCommandHandler(areaService, areaMapper);
  }

  @Test
  public void handle_shouldCreateAreaSuccessfully() {

    // Given
    AreaRequestDTO requestDTO = new AreaRequestDTO(
        "A001",
        "HR Department",
        "Handles HR-related workflows",
        "APP001",
        null,
        null
    );

    CreateAreaCommand command = new CreateAreaCommand(requestDTO);

    Area area = areaMapper.toModel(requestDTO);
    AreaDTO expectedDto = areaMapper.toDTO(area);

    when(areaService.createArea(any(Area.class))).thenReturn(area);

    ResponseEntity<AreaDTO> response = handler.handle(command);
    AreaDTO dto = response.getBody();
    assertNotNull(dto);
    assertEquals(201, response.getStatusCode().value());
    assertEquals(expectedDto.getName(), dto.getName());
    assertEquals(expectedDto.getCode(), dto.getCode());
    assertEquals(expectedDto.getApplicationBase(), dto.getApplicationBase());

    verify(areaService).createArea(any(Area.class));

  }

}
