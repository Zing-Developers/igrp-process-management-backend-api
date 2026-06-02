package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskEventType;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import cv.igrp.platform.process.management.shared.domain.models.ProcessNumber;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;

@Getter
public class TaskInstance {

  private final Identifier id;
  private final Code taskKey;
  private final Code formKey;
  private final Name name;
  private final Code externalId;
  private final Identifier processInstanceId;
  private final ProcessNumber processNumber;
  private final String engineProcessNumber;// used to get variables
  private final Code processName;
  private final Code processKey;
  private final Code businessKey;
  private final Code applicationBase;
  private final String searchTerms;
  private Integer priority;
  private TaskInstanceStatus status;
  private LocalDateTime startedAt;
  private final Code startedBy;
  private LocalDateTime assignedAt;
  private Code assignedBy;
  private LocalDateTime endedAt;
  private Code endedBy;
  private final List<TaskInstanceEvent> taskInstanceEvents;
  private final Set<String> candidateGroups;
  private final Set<String> candidateUsers;
  private final Map<String, Object> variables;
  private final Map<String,Object> forms;
  private Map<String, Object> processVariables;
  private LocalDateTime dueDate;

  private UserProfile userProfileStartedBy;
  private UserProfile userProfileEndedBy;
  private UserProfile userProfileAssignedBy;


  @Builder
  public TaskInstance(
      Identifier id,
      Code taskKey,
      Code formKey,
      Name name,
      Code externalId,
      Identifier processInstanceId,
      Code processName,
      ProcessNumber processNumber,
      String engineProcessNumber,
      Code processKey,
      Code businessKey,
      Code applicationBase,
      String searchTerms,
      Integer priority,
      TaskInstanceStatus status,
      LocalDateTime startedAt,
      Code startedBy,
      LocalDateTime assignedAt,
      Code assignedBy,
      LocalDateTime endedAt,
      Code endedBy,
      List<TaskInstanceEvent> taskInstanceEvents,
      Set<String> candidateGroups,
      Set<String> candidateUsers,
      Map<String, Object> variables,
      Map<String, Object> forms,
      Map<String, Object> processVariables,
      LocalDateTime dueDate,
      UserProfile userProfileStartedBy,
      UserProfile userProfileEndedBy,
      UserProfile userProfileAssignedBy
  ) {
    this.id = id == null ? Identifier.generate() : id;
    this.taskKey = Objects.requireNonNull(taskKey, "Task Key cannot be null!");
    this.formKey = formKey;
    this.name = Objects.requireNonNull(name, "The Name of the task cannot be null!");
    this.externalId = Objects.requireNonNull(externalId, "External Id cannot be null!");
    this.processInstanceId = processInstanceId;
    this.processNumber = processNumber;
    this.processName = processName;
    this.engineProcessNumber = engineProcessNumber;
    this.businessKey = businessKey;
    this.applicationBase = applicationBase;
    this.searchTerms = searchTerms;
    this.priority = priority;
    this.status = status;
    this.startedAt = startedAt;
    this.startedBy = startedBy;
    this.assignedAt = assignedAt;
    this.assignedBy = assignedBy;
    this.endedAt = endedAt;
    this.endedBy = endedBy;
    this.processKey = processKey;
    this.taskInstanceEvents = taskInstanceEvents != null ? taskInstanceEvents : new ArrayList<>();
    this.variables = variables != null ? variables : new HashMap<>();
    this.forms = forms != null ? forms : new HashMap<>();
    this.candidateGroups = candidateGroups != null
        ? candidateGroups
        : new HashSet<>();
    this.candidateUsers = candidateUsers != null
        ? candidateUsers
        : new HashSet<>();
    this.processVariables = processVariables != null ? processVariables : new HashMap<>();
    this.dueDate = dueDate;
    this.userProfileStartedBy = userProfileStartedBy;
    this.userProfileEndedBy = userProfileEndedBy;
    this.userProfileAssignedBy = userProfileAssignedBy;
  }


  public void create() {
    if (processInstanceId == null) {
      throw new IllegalStateException("Process Instance Id cannot be null!");
    }
    if (startedBy == null) {
      throw new IllegalStateException("User cannot be null!");
    }
    this.status = TaskInstanceStatus.CREATED;
    if (startedAt == null)
      this.startedAt = LocalDateTime.now();
    createTaskInstanceEvent(TaskEventType.CREATE, this.startedBy, null);
  }


  public void claim(TaskOperationData data) {
    if (this.status != TaskInstanceStatus.CREATED) {
      throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, String.format("Cannot Claim a Task in Status[%s]", this.status));
    }
    this.assignedBy = Objects.requireNonNull(data.getCurrentUser(), "User cannot be null!");
    this.status = TaskInstanceStatus.ASSIGNED;
    this.assignedAt = LocalDateTime.now();
    createTaskInstanceEvent(TaskEventType.CLAIM, data.getCurrentUser(), data.getNote());
  }


  public void assignUser(TaskOperationData data) {
    if (this.status != TaskInstanceStatus.CREATED) {
      throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, String.format("Cannot Assign a Task in Status[%s]", this.status));
    }
    this.assignedBy = Objects.requireNonNull(data.getTargetUser(), "Target User cannot be null!");
    this.assignedAt = LocalDateTime.now();
    this.status = TaskInstanceStatus.ASSIGNED;
    if (data.getPriority() != null && !data.getPriority().equals(this.priority))
      this.priority = data.getPriority();
    createTaskInstanceEvent(TaskEventType.ASSIGN, data.getCurrentUser(), data.getNote());
  }

  public void unClaim(TaskOperationData data) {
    if (this.status != TaskInstanceStatus.ASSIGNED) {
      throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, String.format("Cannot UnClaim a Task in Status[%s]", this.status));
    }
    this.assignedAt = null;
    this.assignedBy = null;
    this.status = TaskInstanceStatus.CREATED;
    createTaskInstanceEvent(TaskEventType.UNCLAIM, data.getCurrentUser(), data.getNote());
  }


  public void complete(TaskOperationData data) {
    this.endedBy = Objects.requireNonNull(data.getCurrentUser(), "Current User cannot be null!");
    this.endedAt = LocalDateTime.now();
    this.status = TaskInstanceStatus.COMPLETED;
    this.addVariablesAndForms(data);
    createTaskInstanceEvent(TaskEventType.COMPLETE, data.getCurrentUser(), data.getNote());
  }

  private void createTaskInstanceEvent(TaskEventType eventType, Code user, String note) {
    this.taskInstanceEvents.add(
        TaskInstanceEvent.builder()
            .taskInstanceId(Identifier.generate())
            .taskInstanceId(this.id)
            .eventType(eventType)
            .status(this.status)
            .performedBy(user)
            .note(note)
            .build());
  }

  public void addVariablesAndForms(TaskOperationData data) {
    this.variables.putAll(data.getVariables());
    this.forms.putAll(data.getForms());
  }

  public TaskInstance withProperties(ProcessInstance processInstance, Code formKey, Code user) {
    return TaskInstance.builder()
        .id(this.id)
        .taskKey(this.taskKey)
        .externalId(this.externalId)
        .name(this.name)
        .startedAt(this.startedAt)
        .candidateGroups(this.candidateGroups)
        .formKey(formKey != null ? formKey : this.formKey) // if present overrides activity formKey
        .priority(processInstance.getPriority())
        .businessKey(processInstance.getBusinessKey())
        .processInstanceId(processInstance.getId())
        .startedBy(user)
        .build();
  }

  public void addVariables(Map<String, Object> variables) {
    this.variables.putAll(variables);
  }

  public void addVariable(String key, Object value) {
    this.variables.put(key, value);
  }

  public void addProcessVariables(Map<String, Object> variables) {
    this.processVariables.putAll(variables);
  }

  public void resolveCandidateUsers(Collection<String> users) {
    this.candidateUsers.clear();
    if (users == null) {
      return;
    }
    users.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(user -> !user.isBlank())
        .forEach(this.candidateUsers::add);
  }

  public void addCandidateGroup(TaskOperationData data) {
    addCandidates(data);
  }

  public void addCandidates(TaskOperationData data) {
    mergeCandidateGroups(data.getCandidateGroups());
    updateAssignmentMetadata(data.getPriority());

    createTaskInstanceEvent(
        TaskEventType.ASSIGN,
        data.getCurrentUser(),
        data.getNote()
    );
  }

  private void mergeCandidateGroups(List<String> newGroups) {
    if (newGroups == null || newGroups.isEmpty()) {
      return;
    }
    newGroups.stream()
        .filter(g -> !this.candidateGroups.contains(g))
        .forEach(this.candidateGroups::add);
  }

  private void updateAssignmentMetadata(Integer priority) {
    this.assignedAt = LocalDateTime.now();
    this.status = TaskInstanceStatus.ASSIGNED;
    if (priority != null && priority != 0) {
      this.priority = priority;
    }
  }

  public void addCandidateGroup(String groupId, Code user) {
    TaskOperationData taskOperationData = TaskOperationData.builder()
        .currentUser(user)
        .candidateGroups(List.of(groupId))
        .build();
    addCandidateGroup(taskOperationData);
  }

  public void updateDueDate(LocalDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public void resolveUserProfileEndedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileEndedBy = userProfile;
  }

  public void resolveUserProfileStartedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileStartedBy = userProfile;
  }

  public void resolveUserProfileAssignedBy(UserProfile userProfile) {
    if (userProfile == null) {
      throw new IllegalArgumentException("UserProfile cannot be null");
    }
    this.userProfileAssignedBy = userProfile;
  }

}
