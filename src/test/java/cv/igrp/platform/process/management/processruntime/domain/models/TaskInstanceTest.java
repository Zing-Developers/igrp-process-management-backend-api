package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskEventType;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TaskInstanceTest {

  private TaskInstance task;

  @Mock
  private Code currentUser;

  private String taskId;


  @BeforeEach
  void setUp() {

    currentUser = Code.create("demo@nosi.cv");
    taskId = UUID.randomUUID().toString();

    task = TaskInstance.builder()
        .id(Identifier.create(taskId))
        .taskKey(Code.create("T1"))
        .formKey(Code.create("F1"))
        .name(Name.create("Task Test"))
        .externalId(Code.create("EXT"))
        .processInstanceId(Identifier.generate())
        .processNumber(ProcessNumber.create("P123"))
        .applicationBase(Code.create("APP"))
        .processName(Code.create("PROC"))
        .startedBy(Code.create("igrp@nosi.cv"))
        .taskInstanceEvents(new ArrayList<>())
        .build();
  }


  @Test
  void testCreate_ShouldGenerateCreateEvent() {

    task.create();

    assertEquals(TaskInstanceStatus.CREATED, task.getStatus());
    assertNotNull(task.getStartedAt());
    assertEquals(1, task.getTaskInstanceEvents().size());

    TaskInstanceEvent event = task.getTaskInstanceEvents().getFirst();
    assertEquals(TaskEventType.CREATE, event.getEventType());
    assertEquals(TaskInstanceStatus.CREATED, event.getStatus());
  }


  @Test
  void testClaim_ShouldGenerateClaimEvent() {

    var operation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Claiming task")
        .build();

    task.create();

    task.claim(operation);

    assertEquals(TaskInstanceStatus.ASSIGNED, task.getStatus());
    assertNotNull(task.getAssignedAt());
    assertEquals(currentUser, task.getAssignedBy());

    assertFalse(task.getTaskInstanceEvents().isEmpty());
    var lastEvent = task.getTaskInstanceEvents().getLast();
    assertEquals(TaskEventType.CLAIM, lastEvent.getEventType());
    assertEquals(currentUser, lastEvent.getPerformedBy());
  }

  @Test
  void testClaim_ShouldThrow_WhenTaskAlreadyAssigned() {
    var operation1 = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("First claim")
        .build();

    task.create();
    task.claim(operation1); // agora status = ASSIGNED

    var operation2 = TaskOperationData.builder()
        .id(taskId)
        .currentUser(Code.create("another.user@nosi.cv"))
        .note("Trying to claim again")
        .build();

    var ex = assertThrows(IgrpResponseStatusException.class, () -> task.claim(operation2));
    assertTrue(ex.getMessage().contains("Cannot Claim a Task in Status"));
  }


  @Test
  void testUnClaim_ShouldGenerateUnClaimEvent() {

    var claimOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Claiming task")
        .build();

    var unClaimOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Unclaiming task")
        .build();

    task.create();

    task.claim(claimOperation);

    task.unClaim(unClaimOperation);

    assertEquals(TaskInstanceStatus.CREATED, task.getStatus());
    assertNull(task.getAssignedAt());
    assertNull(task.getAssignedBy());

    assertFalse(task.getTaskInstanceEvents().isEmpty());
    var lastEvent = task.getTaskInstanceEvents().getLast();
    assertEquals(TaskEventType.UNCLAIM, lastEvent.getEventType());
    assertEquals(currentUser, lastEvent.getPerformedBy());
  }

  @Test
  void testUnClaim_ShouldThrow_WhenTaskNotAssigned() {

    task.create();

    var unClaimOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(Code.create("another.user@nosi.cv"))
        .note("Trying to claim again")
        .build();

    var ex = assertThrows(IgrpResponseStatusException.class,
        () -> task.unClaim(unClaimOperation));
    assertTrue(ex.getMessage().contains("Cannot UnClaim a Task in Status"));

  }



  @Test
  void testAssign_ShouldSucceed_WhenTaskIsCreated() {

    var targetUser = "mybrother@nosi.cv";

    var operation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .targetUser(targetUser)
        .note("Assign task").build();

    task.create();

    task.assignUser(operation);

    assertEquals(TaskInstanceStatus.ASSIGNED, task.getStatus());
    assertNotNull(task.getAssignedAt());
    assertEquals(Code.create(targetUser), task.getAssignedBy());

    assertFalse(task.getTaskInstanceEvents().isEmpty());
    var lastEvent = task.getTaskInstanceEvents().getLast();
    assertEquals(TaskEventType.ASSIGN, lastEvent.getEventType());
    assertEquals(currentUser, lastEvent.getPerformedBy());
  }

  @Test
  void testAssign_ShouldThrow_WhenTaskAlreadyAssigned() {
    var operation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .targetUser("user1@nosi.cv")
        .note("Assign once").build();

    task.create();
    task.assignUser(operation);

    var operation2 = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .targetUser("user2@nosi.cv")
        .note("Assign again").build();

    var ex = assertThrows(IgrpResponseStatusException.class, () -> task.assignUser(operation2));
    assertTrue(ex.getMessage().contains("Cannot Assign a Task in Status[ASSIGNED]"));
  }

  @Test
  void testAddCandidates_ShouldStoreGroupsAndCreateAssignEvent() {

    var operation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .candidateGroups(List.of("group1", "group2"))
        .note("Assign candidates")
        .build();

    task.create();

    task.addCandidates(operation);

    assertEquals(TaskInstanceStatus.ASSIGNED, task.getStatus());
    assertTrue(task.getCandidateGroups().containsAll(Set.of("group1", "group2")));

    var lastEvent = task.getTaskInstanceEvents().getLast();
    assertEquals(TaskEventType.ASSIGN, lastEvent.getEventType());
    assertEquals(currentUser, lastEvent.getPerformedBy());
  }


  @Test
  void testComplete_ShouldSucceed_WhenTaskIsAssigned() {
    var claimOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Claiming before complete")
        .build();

    task.create();
    task.claim(claimOperation);

    var completeOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Completing the task")
        .build();

    task.complete(completeOperation);

    assertEquals(TaskInstanceStatus.COMPLETED, task.getStatus());
    assertNotNull(task.getEndedAt());
    assertEquals(currentUser, task.getEndedBy());

    var lastEvent = task.getTaskInstanceEvents().getLast();
    assertEquals(TaskEventType.COMPLETE, lastEvent.getEventType());
    assertEquals(currentUser, lastEvent.getPerformedBy());
  }

  @Test
  void testComplete_ShouldSucceed_WhenTaskIsCreated() {
    var completeOperation = TaskOperationData.builder()
        .id(taskId)
        .currentUser(currentUser)
        .note("Trying to complete without claim/assign")
        .build();

    task.create(); // status = CREATED (não ASSIGNED)

    task.complete(completeOperation);

    assertEquals(TaskInstanceStatus.COMPLETED, task.getStatus());
    assertEquals(currentUser, task.getEndedBy());
  }



  @Test
  void withProperties_shouldReturnNewTaskInstanceWithUpdatedFields() {

    var oldForm = "oldForm";
    var newForm = "newForm";


    var original = TaskInstance.builder()
        .id(Identifier.generate())
        .taskKey(Code.create("task-key"))
        .externalId(Code.create("ext-id"))
        .name(Name.create("My Task"))
        .formKey(Code.create(oldForm))
        .startedAt(LocalDateTime.now())
        .candidateGroups(Set.of("group1"))
        .build();

    var processInstance = ProcessInstance.builder()
        .applicationBase(Code.create("APP_BASE"))
        .procReleaseKey(Code.create("PROC_RELEASE_KEY"))
        .procReleaseId(Code.create("PROC_RELEASE_ID"))
        .id(Identifier.generate())
        .name("Process Name")
        .number(ProcessNumber.create("NUM_1234"))
        .businessKey(Code.create("BUS_KEY"))
        .build();

    Code newFormKey = Code.create(newForm);
    Code startedBy = Code.create("igrp@nosi.cv");

    TaskInstance result = original.withProperties(processInstance, newFormKey, startedBy);

    assertEquals(processInstance.getId(), result.getProcessInstanceId());
    assertEquals(newFormKey, result.getFormKey());
    assertEquals(startedBy, result.getStartedBy());

    assertNotEquals(original, result);
    assertEquals(oldForm, original.getFormKey().getValue());
    assertEquals(newForm, result.getFormKey().getValue());
    assertEquals(original.getCandidateGroups(), result.getCandidateGroups());
  }

}
