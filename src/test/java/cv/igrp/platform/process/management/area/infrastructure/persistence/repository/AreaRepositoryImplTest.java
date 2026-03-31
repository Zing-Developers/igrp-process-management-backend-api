package cv.igrp.platform.process.management.area.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.area.domain.models.Area;
import cv.igrp.platform.process.management.area.domain.models.AreaFilter;
import cv.igrp.platform.process.management.area.mappers.AreaMapper;
import cv.igrp.platform.process.management.area.mappers.AreaProcessMapper;
import cv.igrp.platform.process.management.shared.application.constants.Status;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.AreaEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.AreaEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AreaRepositoryImplTest {

  @Mock
  private AreaEntityRepository areaEntityRepository;

  private AreaProcessMapper areaProcessMapper;

  private AreaRepositoryImpl areaRepository;

  private UUID areaId;
  private Area area;
  private AreaEntity areaEntity;

  @BeforeEach
  void setUp() {

    AreaMapper areaMapper = new AreaMapper(new AreaProcessMapper());

    areaRepository = new AreaRepositoryImpl(areaEntityRepository, areaMapper);

    areaId = UUID.randomUUID();

    // Create a real Area
    area = Area.builder()
        .id(Identifier.create(areaId))
        .name(Name.create("AreaName"))
        .applicationBase(Code.create("APP"))
        .code(Code.create("CODE"))
        .status(Status.ACTIVE)
        .description("Description")
        .build();

    areaEntity = new AreaEntity();
    areaEntity.setId(areaId);
    areaEntity.setName("AreaName");
    areaEntity.setApplicationBase("APP");
    areaEntity.setCode("CODE");
    areaEntity.setStatus(Status.ACTIVE);
    areaEntity.setDescription("Description");
  }

  @Test
  void testSave() {
    when(areaEntityRepository.save(any())).thenReturn(areaEntity);

    Area result = areaRepository.save(area);

    assertNotNull(result);
    assertEquals(area.getName().getValue(), result.getName().getValue());
    verify(areaEntityRepository).save(any(AreaEntity.class));
  }

  @Test
  void testFindById_found() {
    when(areaEntityRepository.findById(areaId)).thenReturn(Optional.of(areaEntity));

    Optional<Area> result = areaRepository.findById(areaId);

    assertTrue(result.isPresent());
    assertEquals("AreaName", result.get().getName().getValue());
  }

  @Test
  void testFindById_notFound() {
    when(areaEntityRepository.findById(areaId)).thenReturn(Optional.empty());

    Optional<Area> result = areaRepository.findById(areaId);

    assertFalse(result.isPresent());
  }

  @Test
  void testFindAll() {
    // Arrange
    AreaFilter filter = AreaFilter.builder()
        .page(0)
        .size(10)
        .build();

    PageRequest pageRequest = PageRequest.of(0, 10);

    areaEntity = new AreaEntity();
    areaEntity.setId(UUID.randomUUID());
    areaEntity.setName("Test Area Name");
    areaEntity.setApplicationBase("APP_BASE");
    areaEntity.setCode("AREA_CODE");
    areaEntity.setStatus(Status.ACTIVE);
    areaEntity.setDescription("Test Description");

    Page<AreaEntity> page = new PageImpl<>(List.of(areaEntity), pageRequest, 1);

    when(areaEntityRepository.findAll(any(Specification.class), eq(pageRequest))).thenReturn(page);

    // Act
    PageableLista<Area> result = areaRepository.findAll(filter);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.getContent().size());

    Area mappedArea = result.getContent().getFirst();
    assertEquals("Test Area Name", mappedArea.getName().getValue());
    assertEquals("AREA_CODE", mappedArea.getCode().getValue());
    assertEquals("APP_BASE", mappedArea.getApplicationBase().getValue());
    assertEquals(Status.ACTIVE, mappedArea.getStatus());

    // Pagination assertions
    assertEquals(0, result.getPageNumber());
    assertEquals(10, result.getPageSize());
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertTrue(result.isFirst());
    assertTrue(result.isLast());

    verify(areaEntityRepository).findAll(any(Specification.class), eq(pageRequest));

  }


  @Test
  void testUpdateStatus_found() {

    when(areaEntityRepository.findById(areaId)).thenReturn(Optional.of(areaEntity));
    when(areaEntityRepository.save(areaEntity)).thenReturn(areaEntity);

    areaRepository.updateStatus(areaId, Status.INACTIVE);

    assertEquals(Status.INACTIVE, areaEntity.getStatus());

    verify(areaEntityRepository).save(areaEntity);
  }

  @Test
  void testUpdateStatus_notFound() {
    when(areaEntityRepository.findById(areaId)).thenReturn(Optional.empty());

    assertThrows(IgrpResponseStatusException.class, () ->
        areaRepository.updateStatus(areaId, Status.INACTIVE)
    );

  }
}
