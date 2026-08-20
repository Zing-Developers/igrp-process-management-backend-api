package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.models.ProcessArtifact;
import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.*;
import cv.igrp.platform.process.management.processruntime.domain.repository.*;
import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.application.constants.TaskAssignmentMode;
import cv.igrp.platform.process.management.shared.application.constants.VariableTag;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.ArtifactContext;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.security.util.IgrpAuthorizationConstants;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@Service
public class TaskInstanceService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskInstanceService.class);

  private final TaskInstanceRepository taskInstanceRepository;
  private final TaskInstanceEventRepository taskInstanceEventRepository;
  private final TaskAssignmentRuleRepository taskAssignmentRuleRepository;
  private final RuntimeProcessEngineRepository runtimeProcessEngineRepository;
  private final ProcessInstanceRepository processInstanceRepository;
  private final ProcessDefinitionRepository processDefinitionRepository;
  private final UserProfileRepository userProfileRepository;

  private final UserContext userContext;

  public TaskInstanceService(TaskInstanceRepository taskInstanceRepository,
                             TaskInstanceEventRepository taskInstanceEventRepository,
                             TaskAssignmentRuleRepository taskAssignmentRuleRepository,
                             RuntimeProcessEngineRepository runtimeProcessEngineRepository,
                             ProcessInstanceRepository processInstanceRepository,
                             ProcessDefinitionRepository processDefinitionRepository,
                             UserProfileRepository userProfileRepository,
                             UserContext userContext
  ) {

    this.taskInstanceRepository = taskInstanceRepository;
    this.taskInstanceEventRepository = taskInstanceEventRepository;
    this.taskAssignmentRuleRepository = taskAssignmentRuleRepository;
    this.runtimeProcessEngineRepository = runtimeProcessEngineRepository;
    this.processInstanceRepository = processInstanceRepository;
    this.processDefinitionRepository = processDefinitionRepository;
    this.userProfileRepository = userProfileRepository;
    this.userContext = userContext;
  }


  public void createTaskInstancesByProcess(ProcessInstance processInstance) {
    this.createNextTaskInstances(processInstance, Code.create(processInstance.getStartedBy()));
  }

  public TaskInstance getByIdWihEvents(Identifier id) {
    return taskInstanceRepository.findByIdWithEvents(id.getValue())
        .orElseThrow(() -> IgrpResponseStatusException.notFound("No Task Instance found with id: " + id));
  }

  public void claimTask(TaskOperationData data) {
    var taskInstance = getByIdWihEvents(data.getId());
    taskInstance.claim(data);
    this.save(taskInstance);
    // Call the process engine to claim a task
    runtimeProcessEngineRepository.claimTask(
        taskInstance.getExternalId().getValue(),
        taskInstance.getAssignedBy().getValue()
    );
  }


  public void assignTask(TaskOperationData data) {
    var taskInstance = getByIdWihEvents(data.getId());
    if (data.getTargetUser() != null) {

      taskInstance.assignUser(data);

      runtimeProcessEngineRepository.assignTask(
          taskInstance.getExternalId().getValue(),
          taskInstance.getAssignedBy().getValue(),
          data.getNote()
      );
    } else {

      taskInstance.addCandidates(data);

      data.getCandidateGroups().forEach(group -> runtimeProcessEngineRepository.addCandidateGroup(
          taskInstance.getExternalId().getValue(),
          group
      ));

      var candidateUsers = data.getCandidateUsers().stream()
          .map(this::normalizeUserId)
          .flatMap(Optional::stream)
          .distinct()
          .toList();

      candidateUsers.forEach(user -> runtimeProcessEngineRepository.addCandidateUser(
          taskInstance.getExternalId().getValue(),
          user
      ));

    }

    if (data.getPriority() != null && !data.getPriority().equals(taskInstance.getPriority())) {
      runtimeProcessEngineRepository.setTaskPriority(
          taskInstance.getExternalId().getValue(),
          data.getPriority()
      );
    }

    this.save(taskInstance);

  }

  private Optional<String> normalizeUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(userId.trim());
  }

  public void registerAssignmentRules(ProcessInstance processInstance) {
    var assignmentRules = Optional.ofNullable(processInstance.getAssignmentRules()).orElse(List.of());
    LOGGER.info(
        "Registering [{}] task assignment rule(s) for processInstance [{}], processDefinitionKey [{}]",
        assignmentRules.size(),
        processInstanceIdValue(processInstance),
        processInstance.getProcReleaseKey().getValue()
    );
    assignmentRules
        .forEach(rule -> {
          LOGGER.debug(
              "Registering task assignment rule for processInstance [{}], task [{}], hasAssignee [{}], candidateUsers [{}], candidateGroups [{}], mode [{}], priority [{}]",
              processInstanceIdValue(processInstance),
              rule.getTaskKey().getValue(),
              rule.getAssignee() != null,
              rule.getCandidateUsers() != null ? rule.getCandidateUsers().size() : 0,
              rule.getCandidateGroups() != null ? rule.getCandidateGroups().size() : 0,
              rule.getAssignmentMode(),
              rule.getPriority()
          );
          taskAssignmentRuleRepository.save(TaskAssignmentRule.builder()
            .processDefinitionKey(processInstance.getProcReleaseKey())
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey(rule.getTaskKey())
            .assignee(rule.getAssignee())
            .candidateUsers(rule.getCandidateUsers())
            .candidateGroups(rule.getCandidateGroups())
            .assignmentMode(rule.getAssignmentMode())
            .priority(rule.getPriority())
            .consumed(false)
            .active(true)
            .build()
          );
        });
  }

  public void unClaimTask(TaskOperationData data) {
    var taskInstance = getByIdWihEvents(data.getId());
    taskInstance.unClaim(data);
    this.save(taskInstance);
    // Call the process engine to claim a task
    runtimeProcessEngineRepository.unClaimTask(
        taskInstance.getExternalId().getValue()
    );
  }

  public TaskInstance saveTask(TaskOperationData data) {
    var taskInstance = getByIdWihEvents(data.getId());
    // Validate
    data.validateVariablesAndForms();
    // Save
    taskInstance.addVariablesAndForms(data);
    this.save(taskInstance);
    // Process Engine
    runtimeProcessEngineRepository.saveTask(
        taskInstance.getExternalId().getValue(),
        null,
        data.getVariables()
    );
    return taskInstance;
  }

  public TaskInstance completeTask(TaskOperationData data) {
    data.validateVariablesAndForms();

    var taskInstance = getByIdWihEvents(data.getId());
    taskInstance.complete(data);
    // Save
    taskInstance.addVariablesAndForms(data);
    var completedTask = save(taskInstance);

    // Call the process engine to complete a task
    runtimeProcessEngineRepository.completeTask(
        taskInstance.getExternalId().getValue(),
        null,
        data.getVariables()
    );

    var processInstance = processInstanceRepository
        .findById(taskInstance.getProcessInstanceId().getValue()).orElseThrow(
            () -> IgrpResponseStatusException.notFound("No Process Instance found with id: " + taskInstance.getProcessInstanceId().getValue()));

    var activityProcess = runtimeProcessEngineRepository
        .getProcessInstanceById(processInstance.getEngineProcessNumber().getValue());

    this.createNextTaskInstances(processInstance, data.getCurrentUser());

    if (activityProcess.getStatus() == ProcessInstanceStatus.COMPLETED) {
      processInstance.complete(
          activityProcess.getEndedAt(),
          activityProcess.getEndedBy() != null ? activityProcess.getEndedBy() : data.getCurrentUser().getValue()
      );
      processInstanceRepository.save(processInstance);
    }

    return completedTask;
  }

  public TaskInstance getTaskById(Identifier id) {
    TaskInstance taskInstance = getByIdWihEvents(id);
    // Enrich with process variables
    Map<String, Object> variables = runtimeProcessEngineRepository.getProcessVariables(taskInstance.getEngineProcessNumber());
    taskInstance.addProcessVariables(variables);
    // Resolve user profiles for the task and its events in a single batch query
    resolveAllUserProfiles(List.of(taskInstance));
    return taskInstance;
  }

  /**
   * Searches task instances, scoped to what the caller is allowed to see.
   *
   * <p>Callers without {@link IgrpAuthorizationConstants#TASK_INSTANCES_SEARCH_ALL} only ever see their
   * own tasks and their groups', regardless of the {@code filterByCurrentUser}, {@code user},
   * {@code candidateUsers} and {@code candidateGroups} values sent in the request. This is the single
   * choke point both the general search and the "my tasks" search go through.
   */
  public PageableLista<TaskInstance> getAllTaskInstances(TaskInstanceFilter filter) {

    final var canSearchAll = userContext.isSuperAdmin()
        || userContext.hasPermission(IgrpAuthorizationConstants.TASK_INSTANCES_SEARCH_ALL);

    if (!canSearchAll) {
      filter.restrictToCurrentUser(userContext.getCurrentUser(), userContext.getCurrentGroups());
    } else if (filter.isFilterByCurrentUser()) {
      final var currentUser = userContext.getCurrentUser();
      final var isSuperAdmin = userContext.isSuperAdmin();
      filter.bindCurrentUser(currentUser, isSuperAdmin);
      userContext.getCurrentGroups()
          .forEach(filter::addContextUserGroup);
    }

    // Resolve process-variable filters via the engine into matching engine process numbers.
    // The repository OR-combines these with the task-local (JSONB) variable predicate.
    if (!filter.getVariablesExpressions().isEmpty()) {
      List<ProcessInstance> engineProcessInstances =
          runtimeProcessEngineRepository.getAllProcessInstancesByVariables(filter.getVariablesExpressions());
      engineProcessInstances.forEach(pi ->
          filter.includeEngineProcessNumber(pi.getEngineProcessNumber().getValue()));
    }

    PageableLista<TaskInstance> taskInstances = taskInstanceRepository.findAll(filter);

    // Enrich with process variables — single batch call
    Set<String> uniqueProcessNumbers = taskInstances.getContent().stream()
        .map(TaskInstance::getEngineProcessNumber)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, Map<String, Object>> variablesMap =
        runtimeProcessEngineRepository.getProcessVariablesBatch(uniqueProcessNumbers);

    for (TaskInstance task : taskInstances.getContent()) {
      Map<String, Object> vars = variablesMap.get(task.getEngineProcessNumber());
      if (vars != null) {
        task.addProcessVariables(vars);
      }
    }

    // Resolve all user profiles in a single batch query
    resolveAllUserProfiles(taskInstances.getContent());

    return taskInstances;
  }

  /**
   * Resolves user profiles for all tasks and their events in a single batch query.
   * Collects all user identifiers across the entire page, performs one batch lookup,
   * then distributes profiles back to each task and event.
   */
  private void resolveAllUserProfiles(List<TaskInstance> taskInstances) {
    if (taskInstances == null || taskInstances.isEmpty()) {
      return;
    }

    Set<String> allIds = new HashSet<>();
    for (TaskInstance task : taskInstances) {
      addIfNotNull(allIds, task.getStartedBy());
      addIfNotNull(allIds, task.getEndedBy());
      addIfNotNull(allIds, task.getAssignedBy());
      for (TaskInstanceEvent event : task.getTaskInstanceEvents()) {
        if (event.getPerformedBy() != null) {
          allIds.add(event.getPerformedBy().getValue());
        }
      }
    }

    if (allIds.isEmpty()) {
      return;
    }

    List<UserProfile> profiles = userProfileRepository.findBySubjectOrEmails(allIds, allIds);
    Map<String, UserProfile> lookup = new HashMap<>();
    for (UserProfile p : profiles) {
      if (p.getSub() != null) lookup.put(p.getSub(), p);
      if (p.getEmail() != null) lookup.put(p.getEmail(), p);
    }

    for (TaskInstance task : taskInstances) {
      applyProfile(lookup, task.getStartedBy(), task::resolveUserProfileStartedBy);
      applyProfile(lookup, task.getEndedBy(), task::resolveUserProfileEndedBy);
      applyProfile(lookup, task.getAssignedBy(), task::resolveUserProfileAssignedBy);
      for (TaskInstanceEvent event : task.getTaskInstanceEvents()) {
        applyProfile(lookup, event.getPerformedBy(), event::resolveUserProfilePerformedBy);
      }
    }
  }

  private void applyProfile(Map<String, UserProfile> lookup, Code identifier, Consumer<UserProfile> setter) {
    if (identifier == null) return;
    UserProfile profile = lookup.get(identifier.getValue());
    if (profile != null) setter.accept(profile);
  }

  private void addIfNotNull(Set<String> ids, Code value) {
    if (value != null) {
      ids.add(value.getValue());
    }
  }

  public Map<String, Object> getTaskVariables(Identifier id) {
    var taskInstance = getTaskById(id);
    Map<String, Object> variables = taskInstance.getVariables();
    Map<String, Object> forms = taskInstance.getForms();
    return Map.of(
        VariableTag.FORMS.getCode(), forms,
        VariableTag.VARIABLES.getCode(), variables
    );
  }


  public TaskStatistics getGlobalTaskStatistics() {
    return taskInstanceRepository.getGlobalTaskStatistics();
  }


  public TaskStatistics getTaskStatisticsByUser(Code user, List<String> groups) {
    return taskInstanceRepository.getTaskStatisticsByUser(
        user,
        groups,
        userContext.isSuperAdmin()
    );
  }

  void createNextTaskInstances(ProcessInstance processInstance, Code user) {

    var activeTasks = getActiveRuntimeTasks(processInstance);
    if (activeTasks.isEmpty()) {
      LOGGER.debug(
          "No active runtime tasks found for processInstance [{}] while checking task assignment rules",
          processInstanceIdValue(processInstance)
      );
      return;
    }

    LOGGER.debug(
        "Found [{}] active runtime task(s) for processInstance [{}]; loading task assignment rules for each task",
        activeTasks.size(),
        processInstanceIdValue(processInstance)
    );

    updateRuntimePriorities(activeTasks, processInstance);

    var context = ArtifactContext.from(
        processDefinitionRepository.findAllArtifacts(processInstance.getProcReleaseId())
    );

    for (var runtimeTask : activeTasks) {
      createNextTaskInstance(runtimeTask, processInstance, context, user);
    }

  }

  private void createNextTaskInstance(
      TaskInstance runtimeTask,
      ProcessInstance processInstance,
      ArtifactContext context,
      Code user
  ) {
    var artifact = context.findArtifact(runtimeTask.getTaskKey().getValue());
    var task = runtimeTask.withProperties(
        processInstance,
        context.findFormKey(runtimeTask.getTaskKey().getValue()).orElse(null),
        user
    );

    artifact.ifPresent(processArtifact -> configureDueDate(runtimeTask, task, processArtifact));

    createTask(task);

    applyTaskAssignments(
        task,
        artifact.map(ProcessArtifact::getCandidateGroups).orElse(Set.of()),
        matchingAssignmentRules(processInstance, task),
        user
    );
  }

  private List<TaskAssignmentRule> matchingAssignmentRules(
      ProcessInstance processInstance,
      TaskInstance taskInstance
  ) {
    var persistedRules = taskAssignmentRuleRepository.findActiveByProcessInstanceAndTaskDefinition(
        taskInstance.getProcessInstanceId(),
        taskInstance.getTaskKey()
    );
    LOGGER.debug(
        "Loaded [{}] active and non-consumed task assignment rule(s) from database for processInstance [{}], task [{}]",
        persistedRules.size(),
        taskProcessInstanceIdValue(taskInstance),
        taskInstance.getTaskKey().getValue()
    );
    if (!persistedRules.isEmpty()) {
      return persistedRules;
    }
    var inMemoryRules = Optional.ofNullable(processInstance.getAssignmentRules())
        .orElse(List.of())
        .stream()
        .filter(rule -> rule.matches(taskInstance.getTaskKey()))
        .map(rule -> TaskAssignmentRule.builder()
            .processDefinitionKey(processInstance.getProcReleaseKey())
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey(rule.getTaskKey())
            .assignee(rule.getAssignee())
            .candidateUsers(rule.getCandidateUsers())
            .candidateGroups(rule.getCandidateGroups())
            .assignmentMode(rule.getAssignmentMode())
            .priority(rule.getPriority())
            .consumed(false)
            .active(true)
            .build())
        .toList();
    LOGGER.debug(
        "Using [{}] in-memory task assignment rule(s) for processInstance [{}], task [{}]",
        inMemoryRules.size(),
        processInstanceIdValue(processInstance),
        taskInstance.getTaskKey().getValue()
    );
    return inMemoryRules;
  }

  private String processInstanceIdValue(ProcessInstance processInstance) {
    return processInstance.getId() != null ? processInstance.getId().getValue().toString() : null;
  }

  private String taskProcessInstanceIdValue(TaskInstance taskInstance) {
    return taskInstance.getProcessInstanceId() != null
        ? taskInstance.getProcessInstanceId().getValue().toString()
        : null;
  }

  private void applyTaskAssignments(
      TaskInstance taskInstance,
      Set<String> definitionCandidateGroups,
      List<TaskAssignmentRule> assignmentRules,
      Code user
  ) {
    var assigneeRule = assignmentRules.stream()
        .filter(TaskAssignmentRule::hasAssignee)
        .findFirst();

    if (assigneeRule.isPresent()) {
      applyAssigneeRule(taskInstance, assigneeRule.get(), user);
      return;
    }

    applyDefinitionCandidateGroups(taskInstance, definitionCandidateGroups, user);
    applyCandidateRules(taskInstance, assignmentRules, user);
  }

  private void applyAssigneeRule(TaskInstance taskInstance, TaskAssignmentRule rule, Code user) {
    LOGGER.info(
        "Applying assignee task assignment rule [{}] to taskInstance [{}], task [{}], mode [{}], persisted [{}]",
        rule.getId().getValue(),
        taskInstance.getId().getValue(),
        taskInstance.getTaskKey().getValue(),
        rule.getAssignmentMode(),
        rule.isPersisted()
    );
    assignTask(TaskOperationData.builder()
        .id(taskInstance.getId().getValue().toString())
        .currentUser(user)
        .targetUser(rule.getAssignee().getValue())
        .priority(rule.getPriority())
        .build());
    markConsumedIfOneTime(rule, taskInstance);
  }

  private void applyDefinitionCandidateGroups(TaskInstance taskInstance, Set<String> candidateGroups, Code user) {
    var groups = normalizeCandidateGroups(candidateGroups);
    if (groups.isEmpty()) {
      return;
    }

    assignTask(TaskOperationData.builder()
        .id(taskInstance.getId().getValue().toString())
        .currentUser(user)
        .candidateGroups(groups)
        .build());
  }

  private void applyCandidateRules(
      TaskInstance taskInstance,
      List<TaskAssignmentRule> assignmentRules,
      Code user
  ) {
    assignmentRules.stream()
        .filter(rule -> rule.hasCandidateUsers() || rule.hasCandidateGroups())
        .forEach(rule -> applyCandidateRule(taskInstance, rule, user));
  }

  private void applyCandidateRule(TaskInstance taskInstance, TaskAssignmentRule rule, Code user) {
    LOGGER.info(
        "Applying candidate task assignment rule [{}] to taskInstance [{}], task [{}], candidateUsers [{}], candidateGroups [{}], mode [{}], persisted [{}]",
        rule.getId().getValue(),
        taskInstance.getId().getValue(),
        taskInstance.getTaskKey().getValue(),
        rule.getCandidateUsers() != null ? rule.getCandidateUsers().size() : 0,
        rule.getCandidateGroups() != null ? rule.getCandidateGroups().size() : 0,
        rule.getAssignmentMode(),
        rule.isPersisted()
    );
    assignTask(TaskOperationData.builder()
        .id(taskInstance.getId().getValue().toString())
        .currentUser(user)
        .candidateUsers(rule.getCandidateUsers().stream().toList())
        .candidateGroups(rule.getCandidateGroups().stream().toList())
        .priority(rule.getPriority())
        .build());
    markConsumedIfOneTime(rule, taskInstance);
  }

  private void markConsumedIfOneTime(TaskAssignmentRule rule, TaskInstance taskInstance) {
    if (rule.isPersisted() && rule.getAssignmentMode() == TaskAssignmentMode.ONE_TIME) {
      LOGGER.info(
          "Marking one-time task assignment rule [{}] as consumed by taskInstance [{}]",
          rule.getId().getValue(),
          taskInstance.getId().getValue()
      );
      taskAssignmentRuleRepository.markConsumed(rule.getId(), taskInstance.getId());
    }
  }

  private List<String> normalizeCandidateGroups(Set<String> candidateGroups) {
    if (candidateGroups == null || candidateGroups.isEmpty()) {
      return List.of();
    }
    return candidateGroups.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(group -> !group.isBlank())
        .distinct()
        .toList();
  }

  private List<TaskInstance> getActiveRuntimeTasks(ProcessInstance processInstance) {
    return runtimeProcessEngineRepository.getActiveTaskInstances(
        processInstance.getEngineProcessNumber().getValue()
    );
  }

  private void updateRuntimePriorities(List<TaskInstance> tasks, ProcessInstance processInstance) {
    tasks.forEach(task ->
        runtimeProcessEngineRepository.setTaskPriority(
            task.getExternalId().getValue(),
            processInstance.getPriority()
        )
    );
  }

  private void configureDueDate(TaskInstance runtimeTask, TaskInstance task, ProcessArtifact artifact) {
    LOGGER.debug("DueDate: {} from ProcessArtifact: {}", artifact.getDueDate(), artifact.getKey());
    if (artifact.getDueDate() == null || artifact.getDueDate().isBlank()) {
      return;
    }
    LocalDateTime dueDate = LocalDateTime.now().plus(Duration.parse(artifact.getDueDate()));
    task.updateDueDate(dueDate);
    runtimeProcessEngineRepository.setTaskDueDate(
        runtimeTask.getExternalId().getValue(),
        dueDate
    );
  }


  public void createTask(TaskInstance taskInstance) {
    taskInstance.create();
    taskInstanceRepository.create(taskInstance);
    this.saveCurrentEvent(taskInstance.getTaskInstanceEvents().getFirst());
  }


  public TaskInstance save(TaskInstance taskInstance) {
    taskInstanceRepository.update(taskInstance);
    this.saveCurrentEvent(taskInstance.getTaskInstanceEvents().getLast());
    return taskInstance;
  }

  private void saveCurrentEvent(TaskInstanceEvent taskInstanceEvent) {
    taskInstanceEvent.create();
    taskInstanceEventRepository.save(taskInstanceEvent);
  }

}
