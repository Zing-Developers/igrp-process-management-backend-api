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
  void testGetGlobalTaskStatistics() {

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
    TaskStatistics stats = repository.getGlobalTaskStatistics();

    // Then
    assertEquals(100L, stats.getTotalTaskInstances());
    assertEquals(52L, stats.getTotalAvailableTasks());
    assertEquals(18L, stats.getTotalAssignedTasks());
    assertEquals(5L, stats.getTotalSuspendedTasks());
    assertEquals(25L, stats.getTotalCompletedTasks());
    assertEquals(7L, stats.getTotalCanceledTasks());

    assertThat(specCaptor.getAllValues()).hasSize(5);

  }



  @Test
  void testGetTaskStatisticsByUser() {

    String user = currentUser.getValue();

    // Create entities with different statuses visible to the user
    TaskInstanceEntity created1 = new TaskInstanceEntity();
    created1.setStatus(TaskInstanceStatus.CREATED);
    created1.setAssignedBy(user);

    TaskInstanceEntity assigned1 = new TaskInstanceEntity();
    assigned1.setStatus(TaskInstanceStatus.ASSIGNED);
    assigned1.setAssignedBy(user);

    TaskInstanceEntity completed1 = new TaskInstanceEntity();
    completed1.setStatus(TaskInstanceStatus.COMPLETED);
    completed1.setEndedBy(user);

    when(entityRepository.findAll()).thenReturn(List.of(created1, assigned1, completed1));

    // When - isSuperAdmin=true so all tasks are visible
    TaskStatistics stats = repository.getTaskStatisticsByUser(currentUser, null, true);

    // Then
    assertEquals(3L, stats.getTotalTaskInstances());
    assertEquals(1L, stats.getTotalAvailableTasks());
    assertEquals(1L, stats.getTotalAssignedTasks());
    assertEquals(0L, stats.getTotalSuspendedTasks());
    assertEquals(1L, stats.getTotalCompletedTasks());
    assertEquals(0L, stats.getTotalCanceledTasks());

  }

}
