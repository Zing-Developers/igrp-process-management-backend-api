package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskStatistics;
import cv.igrp.platform.process.management.processruntime.mappers.TaskInstanceMapper;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.TaskInstanceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskInstanceRepositoryImplTest {

  @Mock
  private TaskInstanceEntityRepository entityRepository;

  @Mock
  private TaskInstanceMapper mapper;

  @InjectMocks
  private TaskInstanceRepositoryImpl repository;

  private UUID taskId;
  private Code currentUser;


  @BeforeEach
  void setUp() {
    taskId = UUID.randomUUID();
    currentUser = Code.create("demo@nosi.cv");
  }



  @Test
  void create_shouldThrowException_whenMapperFails() {

    var modelMock = mock(TaskInstance.class);

    when(mapper.toNewTaskEntity(modelMock)).thenThrow(new RuntimeException("Mapper failed"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> repository.create(modelMock));

    assertEquals("Mapper failed", ex.getMessage());
    verify(mapper).toNewTaskEntity(modelMock);
    verifyNoInteractions(entityRepository);

  }



  @Test
  void create_shouldThrowException_whenEntitySaveFails() {

    var modelMock = mock(TaskInstance.class);
    var entityMock = mock(TaskInstanceEntity.class);

    when(mapper.toNewTaskEntity(modelMock)).thenReturn(entityMock);
    when(entityRepository.save(entityMock)).thenThrow(new RuntimeException("DB error"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> repository.create(modelMock));

    assertEquals("DB error", ex.getMessage());
    verify(mapper).toNewTaskEntity(modelMock);
    verify(entityRepository).save(entityMock);
    verifyNoMoreInteractions(mapper);

  }



  @Test
  void update_shouldThrowNotFound_whenEntityDoesNotExist() {

    var modelMock = mock(TaskInstance.class);

    when(modelMock.getId()).thenReturn(Identifier.create(taskId));
    when(entityRepository.findById(taskId)).thenReturn(Optional.empty());

    assertThrows(IgrpResponseStatusException.class,
        () -> repository.update(modelMock));

    verify(entityRepository).findById(taskId);
    verifyNoInteractions(mapper);

  }


  @Test
  void update_shouldThrowException_whenMapperFails() {

    var modelMock = mock(TaskInstance.class);
    var entityMock = mock(TaskInstanceEntity.class);

    when(modelMock.getId()).thenReturn(Identifier.create(taskId));
    when(entityRepository.findById(taskId)).thenReturn(Optional.of(entityMock));
    doThrow(new RuntimeException("Mapper update failed"))
        .when(mapper).toTaskEntity(modelMock, entityMock);

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> repository.update(modelMock));

    assertEquals("Mapper update failed", ex.getMessage());
    verify(mapper).toTaskEntity(modelMock, entityMock);
    verifyNoMoreInteractions(entityRepository);

  }


  @Test
  void testFindByFilter() {

    var processName = Name.create("TestProcess");

    var filter = TaskInstanceFilter
        .builder()
        .page(0)
        .size(10)
        .processName(processName)
        .status(TaskInstanceStatus.CREATED)
        .build();

    TaskInstanceEntity entity1 = mock(TaskInstanceEntity.class);
    TaskInstanceEntity entity2 = mock(TaskInstanceEntity.class);

    List<TaskInstanceEntity> entities = List.of(entity1, entity2);
    Page<TaskInstanceEntity> page = new PageImpl<>(entities, PageRequest.of(filter.getPage(), filter.getSize()), 2);

    when(entityRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(page);

    // mock do mapper
    TaskInstance model1 = mock(TaskInstance.class);
    TaskInstance model2 = mock(TaskInstance.class);

    // when
    when(mapper.toModel(entity1)).thenReturn(model1);
    when(mapper.toModel(entity2)).thenReturn(model2);

    PageableLista<TaskInstance> result = repository.findAll(filter);

    // then
    assertNotNull(result);
    assertEquals(2, result.getTotalElements());
    assertEquals(1, result.getTotalPages());
    assertEquals(2, result.getContent().size());
    assertEquals(model1, result.getContent().get(0));
    assertEquals(model2, result.getContent().get(1));

    verify(entityRepository).findAll(any(Specification.class), any(PageRequest.class));
    verify(mapper).toModel(entity1);
    verify(mapper).toModel(entity2);

  }


  @Test
  void testFindByFilter_withPriority() {

    var filter = TaskInstanceFilter
        .builder()
        .page(0)
        .size(10)
        .priority(5)
        .build();

    TaskInstanceEntity entity = mock(TaskInstanceEntity.class);
    Page<TaskInstanceEntity> page =
        new PageImpl<>(List.of(entity), PageRequest.of(filter.getPage(), filter.getSize()), 1);

    when(entityRepository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(page);

    TaskInstance model = mock(TaskInstance.class);
    when(mapper.toModel(entity)).thenReturn(model);

    PageableLista<TaskInstance> result = repository.findAll(filter);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getContent().size());
    assertEquals(model, result.getContent().get(0));

    verify(entityRepository).findAll(any(Specification.class), any(PageRequest.class));
    verify(mapper).toModel(entity);

  }



  @Test
  void testGetGlobalTaskStatistics() {

    // Single grouped aggregate replaces the previous per-status COUNT queries.
    when(entityRepository.countGroupedByStatus()).thenReturn(List.of(
        statusCount(TaskInstanceStatus.CREATED, 52L),    // available
        statusCount(TaskInstanceStatus.ASSIGNED, 18L),
        statusCount(TaskInstanceStatus.SUSPENDED, 5L),
        statusCount(TaskInstanceStatus.COMPLETED, 25L),
        statusCount(TaskInstanceStatus.CANCELED, 7L)
    ));

    // When
    TaskStatistics stats = repository.getGlobalTaskStatistics();

    // Then
    assertEquals(107L, stats.getTotalTaskInstances()); // total = sum of all buckets
    assertEquals(52L, stats.getTotalAvailableTasks());
    assertEquals(18L, stats.getTotalAssignedTasks());
    assertEquals(5L, stats.getTotalSuspendedTasks());
    assertEquals(25L, stats.getTotalCompletedTasks());
    assertEquals(7L, stats.getTotalCanceledTasks());
  }

  @Test
  void testGetGlobalTaskStatistics_missingStatusDefaultsToZero() {

    // Only two statuses present; the rest must default to 0.
    when(entityRepository.countGroupedByStatus()).thenReturn(List.of(
        statusCount(TaskInstanceStatus.CREATED, 10L),
        statusCount(TaskInstanceStatus.ASSIGNED, 3L)
    ));

    TaskStatistics stats = repository.getGlobalTaskStatistics();

    assertEquals(13L, stats.getTotalTaskInstances());
    assertEquals(10L, stats.getTotalAvailableTasks());
    assertEquals(3L, stats.getTotalAssignedTasks());
    assertEquals(0L, stats.getTotalSuspendedTasks());
    assertEquals(0L, stats.getTotalCompletedTasks());
    assertEquals(0L, stats.getTotalCanceledTasks());
  }

  private static TaskInstanceEntityRepository.TaskStatusCount statusCount(TaskInstanceStatus status, long count) {
    return new TaskInstanceEntityRepository.TaskStatusCount() {
      @Override
      public TaskInstanceStatus getStatus() {
        return status;
      }

      @Override
      public long getCount() {
        return count;
      }
    };
  }



  @Test
  void testGetTaskStatisticsByUser() {

    // total
    when(entityRepository.count()).thenReturn(100L);

    // captor para Specification
    ArgumentCaptor<Specification<TaskInstanceEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);

    when(entityRepository.count(specCaptor.capture()))
        .thenReturn(52L)   // available
        .thenReturn(18L)   // assigned
        .thenReturn(5L)    // suspended
        .thenReturn(25L)   // completed
        .thenReturn(7L);   // canceled

    // When
    TaskStatistics stats = repository.getTaskStatisticsByUser(currentUser, null, false);

    // Then
    assertEquals(100L, stats.getTotalTaskInstances());
    assertEquals(52L, stats.getTotalAvailableTasks());
    assertEquals(18L, stats.getTotalAssignedTasks());
    assertEquals(5L, stats.getTotalSuspendedTasks());
    assertEquals(25L, stats.getTotalCompletedTasks());
    assertEquals(7L, stats.getTotalCanceledTasks());

    assertThat(specCaptor.getAllValues()).hasSize(5);

  }

}
