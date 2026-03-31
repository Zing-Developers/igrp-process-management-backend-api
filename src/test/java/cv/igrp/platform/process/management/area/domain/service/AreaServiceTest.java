package cv.igrp.platform.process.management.area.domain.service;

import cv.igrp.platform.process.management.area.domain.models.Area;
import cv.igrp.platform.process.management.area.domain.models.AreaFilter;
import cv.igrp.platform.process.management.area.domain.models.AreaProcess;
import cv.igrp.platform.process.management.area.domain.models.AreaProcessFilter;
import cv.igrp.platform.process.management.area.domain.repository.AreaProcessRepository;
import cv.igrp.platform.process.management.area.domain.repository.AreaRepository;
import cv.igrp.platform.process.management.shared.application.constants.Status;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

  @Mock
  private AreaRepository areaRepository;

  @Mock
  private AreaProcessRepository areaProcessRepository;

  @InjectMocks
  private AreaService areaService;

  private UUID areaId;
  private Area area;
  private AreaProcess areaProcess;

  @BeforeEach
  void setUp() {
    areaId = UUID.randomUUID();
    area = Area.builder()
        .code(Code.create("area_1"))
        .name(Name.create("Area 1"))
        .id(Identifier.create(areaId))
        .status(Status.ACTIVE)
        .applicationBase(Code.create("igrp-app"))
        .description("Area 1")
        .build();
    areaProcess = AreaProcess.builder()
        .id(Identifier.generate())
        .processKey(Code.create("invoicing_process_1"))
        .releaseId(Code.create("123456"))
        .status(Status.ACTIVE)
        .build();
  }

  @Test
  void testGetAreaById_found() {
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    Area result = areaService.getAreaById(areaId);
    assertEquals(area, result);
  }

  @Test
  void testGetAreaById_notFound() {
    when(areaRepository.findById(areaId)).thenReturn(Optional.empty());
    assertThrows(IgrpResponseStatusException.class, () -> areaService.getAreaById(areaId));
  }

  @Test
  void testGetAllAreas() {
    AreaFilter filter = AreaFilter.builder()
        .build();

    PageableLista<Area> areaPageableLista = new PageableLista<>(
        0,
        50,
        1L,
        1,
        true,
        true,
        List.of(area)
    );

    when(areaRepository.findAll(filter)).thenReturn(areaPageableLista);

    PageableLista<Area> result = areaService.getAllAreas(filter);

    assertNotNull(result);
    assertEquals(0, result.getPageNumber());
    assertEquals(50, result.getPageSize());
    assertEquals(1L, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertTrue(result.isFirst());
    assertTrue(result.isLast());


    List<Area> areas =  result.getContent();

    assertNotNull(areas);
    assertEquals(1, areas.size());
    assertEquals(area.getId(), areas.getFirst().getId());
  }

  @Test
  void testCreateArea() {
    when(areaRepository.save(area)).thenReturn(area);
    Area result = areaService.createArea(area);
    assertEquals(area, result);
    verify(areaRepository).save(area);
  }

  @Test
  void testUpdateArea() {
    Area updated = Area.builder()
        .code(Code.create("new_code"))
        .name(Name.create("New Name"))
        .applicationBase(Code.create("igrp-app"))
        .build();
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(areaRepository.save(area)).thenReturn(area);

    Area result = areaService.updateArea(areaId, updated);
    assertEquals(area.getId(), result.getId());
    verify(areaRepository).save(area);
  }

  @Test
  void testDeleteArea() {
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

    areaService.deleteArea(areaId);

    verify(areaRepository).delete(areaId);
  }

  @Test
  void testCreateProcessDefinition_newProcess() {
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(areaProcessRepository.save(any())).thenReturn(areaProcess);

    AreaProcess result = areaService.createProcessDefinition(areaId, areaProcess);

    assertEquals(areaProcess, result);
    verify(areaProcessRepository).save(areaProcess);
  }

  @Test
  void testCreateProcessDefinition_existingProcess() {
    area.add(areaProcess);
    areaProcess.reActivate();

    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(areaProcessRepository.save(areaProcess)).thenReturn(areaProcess);

    AreaProcess result = areaService.createProcessDefinition(areaId, areaProcess);

    assertEquals(areaProcess, result);
    verify(areaProcessRepository).save(areaProcess);
  }

  @Test
  void testRemoveProcessDefinition() {
    UUID processId = areaProcess.getId().getValue();
    area.add(areaProcess);

    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(areaProcessRepository.findById(processId)).thenReturn(Optional.of(areaProcess));

    areaService.removeProcessDefinition(areaId, processId);

    verify(areaProcessRepository).delete(processId);
  }

  @Test
  void testGetAreaProcessById_found() {
    UUID processId = areaProcess.getId().getValue();
    when(areaProcessRepository.findById(processId)).thenReturn(Optional.of(areaProcess));

    AreaProcess result = areaService.getAreaProcessById(processId);
    assertEquals(areaProcess.getId(), result.getId());
  }

  @Test
  void testGetAreaProcessById_notFound() {
    UUID processId = UUID.randomUUID();
    when(areaProcessRepository.findById(processId)).thenReturn(Optional.empty());

    assertThrows(IgrpResponseStatusException.class, () -> areaService.getAreaProcessById(processId));
  }

  @Test
  void testGetAllAreaProcess() {
    AreaProcessFilter filter = AreaProcessFilter.builder()
        .build();

    PageableLista<AreaProcess> areaProcessPageableLista = new PageableLista<>(
        0,
        50,
        1L,
        1,
        true,
        true,
        List.of(areaProcess)
    );

    when(areaProcessRepository.findAll(filter)).thenReturn(areaProcessPageableLista);

    PageableLista<AreaProcess> result = areaService.getAllAreaProcess(filter);

    assertNotNull(result);
    assertEquals(0, result.getPageNumber());
    assertEquals(50, result.getPageSize());
    assertEquals(1L, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertTrue(result.isFirst());
    assertTrue(result.isLast());

    List<AreaProcess> areaProcesses = result.getContent();

    assertNotNull(areaProcesses);
    assertEquals(1, areaProcesses.size());
    assertEquals(areaProcess.getId(), areaProcesses.getFirst().getId());

  }

}
