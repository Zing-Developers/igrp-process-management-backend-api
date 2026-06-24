package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskStatistics;
import cv.igrp.platform.process.management.processruntime.domain.models.VariablesExpression;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskInstanceRepository;
import cv.igrp.platform.process.management.processruntime.mappers.TaskInstanceMapper;
import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.TaskInstanceEntityRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class TaskInstanceRepositoryImpl implements TaskInstanceRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskInstanceRepositoryImpl.class);

  private final TaskInstanceEntityRepository taskInstanceEntityRepository;
  private final TaskInstanceMapper taskMapper;

  public TaskInstanceRepositoryImpl(TaskInstanceEntityRepository taskInstanceEntityRepository,
                                    TaskInstanceMapper taskMapper) {

    this.taskInstanceEntityRepository = taskInstanceEntityRepository;
    this.taskMapper = taskMapper;
  }


  @Override
  public Optional<TaskInstance> findById(UUID id) {
    return taskInstanceEntityRepository.findById(id).map(taskMapper::toModel);
  }


  @Override
  public Optional<TaskInstance> findByIdWithEvents(UUID id) {
    return taskInstanceEntityRepository.findById(id).map(t -> taskMapper.toModel(t, true));
  }


  @Override
  @Transactional
  public void create(TaskInstance taskInstance) {
    taskInstanceEntityRepository.save(taskMapper.toNewTaskEntity(taskInstance));
  }


  @Override
  @Transactional
  public void update(TaskInstance taskInstance) {
    var taskInstanceEntity = taskInstanceEntityRepository
        .findById(taskInstance.getId().getValue())
        .orElseThrow(() -> IgrpResponseStatusException.notFound(
            "No Task Instance found with id: " + taskInstance.getId().getValue()));
    taskMapper.toTaskEntity(taskInstance, taskInstanceEntity);
    taskInstanceEntityRepository.save(taskInstanceEntity);
  }


  @Override
  public PageableLista<TaskInstance> findAll(TaskInstanceFilter filter) {

    Specification<TaskInstanceEntity> spec = buildSpecification(filter);

    PageRequest pageRequest = filter.isFilterByCurrentUser() && !filter.isSuperAdmin()
        ? PageRequest.of(filter.getPage(), filter.getSize())
        : PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(Sort.Direction.DESC, "startedAt"));

    Page<TaskInstanceEntity> page = taskInstanceEntityRepository.findAll(spec, pageRequest);

    LOGGER.debug("Task query returned {} results (page {}/{}, totalElements={})",
        page.getNumberOfElements(), page.getNumber(), page.getTotalPages(), page.getTotalElements());

    List<TaskInstance> content = page.getContent().stream()
        .map(taskMapper::toModel)
        .toList();

    return new PageableLista<>(
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast(),
        page.isFirst(),
        content
    );
  }


  private Specification<TaskInstanceEntity> buildSpecification(TaskInstanceFilter filter) {

    LOGGER.debug("Filter: {}", filter);

    Specification<TaskInstanceEntity> spec = (_, _, _) -> null;

    spec = spec.and((root, _, cb) ->
        cb.equal(root.get("processInstanceId").get("isArchived"), filter.isArchived()));

    if (filter.getProcessInstanceId() != null) {
      spec = spec.and((root, _, cb) ->
          cb.equal(root.get("processInstanceId").get("id"), filter.getProcessInstanceId().getValue()));
    }

    if (filter.getProcessNumber() != null) {
      spec = spec.and((root, _, cb) ->
          cb.or(cb.equal(root.get("processInstanceId").get("number"), filter.getProcessNumber().getValue()),
              cb.equal(root.get("processInstanceId").get("businessKey"), filter.getProcessNumber().getValue())));
    }

    if (filter.getApplicationBase() != null) {
      spec = spec.and((root, _, cb) ->
          cb.equal(root.get("processInstanceId").get("applicationBase"), filter.getApplicationBase().getValue()));
    }

    if (filter.getName() != null) {
      spec = spec.and((root, _, cb) ->
          cb.like(root.get("name"), "%" + filter.getName().getValue() + "%"));
    }

    if (filter.getProcessName() != null) {
      spec = spec.and((root, _, cb) ->
          cb.like(root.get("processInstanceId").get("name"), "%" + filter.getProcessName().getValue() + "%"));
    }

    if (filter.getProcessRealeaseKey() != null) {
      spec = spec.and((root, _, cb) ->
          cb.equal(root.get("processInstanceId").get("procReleaseKey"), filter.getProcessRealeaseKey().getValue()));
    }

    if (filter.getStatus() != null) {
      spec = spec.and((root, _, cb) ->
          cb.equal(root.get("status"), filter.getStatus().getCode()));
    }

    if (filter.getPriority() != null) {
      spec = spec.and((root, _, cb) ->
          cb.equal(root.get("priority"), filter.getPriority()));
    }

    if (filter.getDateFrom() != null) {
      spec = spec.and((root, _, cb) ->
          cb.greaterThanOrEqualTo(root.get("startedAt"), filter.getDateFrom().atStartOfDay()));
    }

    if (filter.getDateTo() != null) {
      spec = spec.and((root, _, cb) ->
          cb.lessThanOrEqualTo(root.get("startedAt"), filter.getDateTo().atTime(LocalTime.MAX)));
    }

    if (!filter.getVariablesExpressions().isEmpty()) {
      spec = spec.and((root, _, cb) -> {
        // Task-local variables (JSONB column) — all expressions ANDed together
        Predicate jsonbMatch = cb.and(filter.getVariablesExpressions()
            .stream()
            .map(expr -> buildVariablePredicate(expr, root, cb))
            .toArray(Predicate[]::new));
        // Process variables — resolved by the engine into matching engine process numbers.
        // Empty list means the engine matched nothing, so the process side contributes nothing
        // and the result depends solely on the task-local match.
        Predicate processMatch = filter.getEngineProcessNumbers().isEmpty()
            ? cb.disjunction()
            : root.get("processInstanceId").get("engineProcessNumber").in(filter.getEngineProcessNumbers());
        // A task matches if EITHER its process variables OR its task-local variables match.
        return cb.or(processMatch, jsonbMatch);
      });
    }

    // User visibility: assigned to current user OR rule candidate user OR user is in a candidate group
    if (filter.isFilterByCurrentUser() && !filter.isSuperAdmin()) {
      String currentUser = filter.getUser() != null ? filter.getUser().getValue() : null;
      Set<String> groups = filter.getContextUserGroups();
      LOGGER.debug("Adding visibility spec for user [{}] with groups {}", currentUser, groups);
      spec = spec.and(userVisibilitySpec(currentUser, groups));
      spec = spec.and(userVisibilityOrderSpec(currentUser, groups));
    }

    // Client-supplied candidate groups filter
    if (filter.getCandidateGroups() != null && !filter.getCandidateGroups().isEmpty()) {
      spec = spec.and((root, query, cb) ->
          candidateGroupExistsPredicate(root, query, cb, filter.getCandidateGroups()));
    }

    if (filter.getCandidateUsers() != null && !filter.getCandidateUsers().isEmpty()) {
      spec = spec.and((root, query, cb) ->
          candidateUserRulePredicate(root, query, cb, filter.getCandidateUsers()));
    }

    return spec;
  }


  private Predicate buildVariablePredicate(
      VariablesExpression expr,
      Root<TaskInstanceEntity> root,
      CriteriaBuilder cb
  ) {
    Object value = expr.getValue();

    // Build JSON expression, prepending "variables" as top-level key
    Expression<String> jsonExpr = buildJsonPathExpression(root, cb, expr.getName());

    // Create operator predicate depending on type
    return buildOperatorPredicate(jsonExpr, expr, value, cb);
  }


  private Expression<String> buildJsonPathExpression(
      Root<TaskInstanceEntity> root,
      CriteriaBuilder cb,
      String variableName
  ) {
    String[] pathSegments = variableName.split("\\.");

    Expression<?> expr = root.get("variables");

    for (String key : pathSegments) {
      expr = cb.function(
          "jsonb_extract_path_text",
          String.class,
          expr,
          cb.literal(key)
      );
    }

    return (Expression<String>) expr;
  }


  private Predicate buildOperatorPredicate(
      Expression<String> jsonExpr,
      VariablesExpression expr,
      Object value,
      CriteriaBuilder cb
  ) {
    // ----------------------------
    // NUMBER
    // ----------------------------
    if (value instanceof Number number) {
      Expression<BigDecimal> numExpr = cb.function(
          "to_number",
          BigDecimal.class,
          jsonExpr,
          cb.literal("999999999.999999")
      );
      BigDecimal val = new BigDecimal(number.toString());

      return switch (expr.getOperator()) {
        case EQUALS -> cb.equal(numExpr, val);
        case NOT_EQUALS -> cb.notEqual(numExpr, val);
        case GREATER_THAN -> cb.greaterThan(numExpr, val);
        case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo(numExpr, val);
        case LESS_THAN -> cb.lessThan(numExpr, val);
        case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo(numExpr, val);
        default -> throw new IllegalArgumentException(
            "Operator " + expr.getOperator() + " not supported for NUMBER"
        );
      };
    }

    // ----------------------------
    // DATE (LocalDate)
    // ----------------------------
    if (value instanceof LocalDate date) {
      Expression<LocalDate> dateExpr = cb.function(
          "to_date",
          LocalDate.class,
          jsonExpr,
          cb.literal("YYYY-MM-DD")
      );

      return switch (expr.getOperator()) {
        case EQUALS -> cb.equal(dateExpr, date);
        case NOT_EQUALS -> cb.notEqual(dateExpr, date);
        case GREATER_THAN -> cb.greaterThan(dateExpr, date);
        case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo(dateExpr, date);
        case LESS_THAN -> cb.lessThan(dateExpr, date);
        case LESS_THAN_OR_EQUAL -> cb.lessThanOrEqualTo(dateExpr, date);
        default -> throw new IllegalArgumentException(
            "Operator " + expr.getOperator() + " not supported for DATE"
        );
      };
    }

    // ----------------------------
    // BOOLEAN
    // ----------------------------
    if (value instanceof Boolean bool) {
      return switch (expr.getOperator()) {
        case EQUALS -> cb.equal(jsonExpr, bool.toString());
        case NOT_EQUALS -> cb.notEqual(jsonExpr, bool.toString());
        default -> throw new IllegalArgumentException(
            "Operator " + expr.getOperator() + " not supported for BOOLEAN"
        );
      };
    }

    // ----------------------------
    // STRING (default)
    // ----------------------------
    String strVal = value.toString();
    return switch (expr.getOperator()) {
      case EQUALS -> cb.equal(jsonExpr, strVal);
      case EQUALS_IGNORE_CASE -> cb.equal(cb.lower(jsonExpr), strVal.toLowerCase());
      case NOT_EQUALS -> cb.notEqual(jsonExpr, strVal);
      case NOT_EQUALS_IGNORE_CASE -> cb.notEqual(cb.lower(jsonExpr), strVal.toLowerCase());
      case LIKE -> cb.like(jsonExpr, "%" + strVal + "%");
      case LIKE_IGNORE_CASE -> cb.like(cb.lower(jsonExpr), "%" + strVal.toLowerCase() + "%");
      default -> throw new IllegalArgumentException(
          "Operator " + expr.getOperator() + " not supported for STRING"
      );
    };
  }


  @Override
  public TaskStatistics getGlobalTaskStatistics() {

    long total = taskInstanceEntityRepository.count();
    long available = countBySpec(statusSpec(TaskInstanceStatus.CREATED));
    long assigned = countBySpec(statusSpec(TaskInstanceStatus.ASSIGNED));
    long suspended = countBySpec(statusSpec(TaskInstanceStatus.SUSPENDED));
    long completed = countBySpec(statusSpec(TaskInstanceStatus.COMPLETED));
    long canceled = countBySpec(statusSpec(TaskInstanceStatus.CANCELED));

    return TaskStatistics.builder()
        .totalTaskInstances(total)
        .totalAvailableTasks(available)
        .totalAssignedTasks(assigned)
        .totalSuspendedTasks(suspended)
        .totalCompletedTasks(completed)
        .totalCanceledTasks(canceled)
        .build();
  }


  @Override
  public TaskStatistics getTaskStatisticsByUser(Code user, List<String> groups, boolean isSuperAdmin) {

    String currentUser = user.getValue();
    Set<String> userGroups = groups == null
        ? Set.of()
        : groups.stream().map(String::trim).collect(Collectors.toSet());

    // Base visibility: tasks assigned to user OR user is in candidate group
    Specification<TaskInstanceEntity> visibilitySpec = isSuperAdmin
        ? Specification.allOf()
        : userVisibilitySpec(currentUser, userGroups);

    long total = taskInstanceEntityRepository.count();

    // Available: CREATED tasks visible to the user (claimable)
    long available = countBySpec(visibilitySpec.and(statusSpec(TaskInstanceStatus.CREATED)));

    // Assigned: ASSIGNED tasks where user is the assignee
    long assigned = countBySpec(statusSpec(TaskInstanceStatus.ASSIGNED)
        .and((root, _, cb) -> cb.equal(root.get("assignedBy"), currentUser)));

    // Suspended: SUSPENDED tasks where user is the assignee
    long suspended = countBySpec(statusSpec(TaskInstanceStatus.SUSPENDED)
        .and((root, _, cb) -> cb.equal(root.get("assignedBy"), currentUser)));

    // Completed: COMPLETED tasks where user ended them
    long completed = countBySpec(statusSpec(TaskInstanceStatus.COMPLETED)
        .and((root, _, cb) -> cb.equal(root.get("endedBy"), currentUser)));

    // Canceled: CANCELED tasks where user ended them
    long canceled = countBySpec(statusSpec(TaskInstanceStatus.CANCELED)
        .and((root, _, cb) -> cb.equal(root.get("endedBy"), currentUser)));

    return TaskStatistics.builder()
        .totalTaskInstances(total)
        .totalAvailableTasks(available)
        .totalAssignedTasks(assigned)
        .totalSuspendedTasks(suspended)
        .totalCompletedTasks(completed)
        .totalCanceledTasks(canceled)
        .build();
  }

  private Specification<TaskInstanceEntity> statusSpec(TaskInstanceStatus status) {
    return (root, _, cb) -> cb.equal(root.get("status"), status);
  }

  private Specification<TaskInstanceEntity> userVisibilitySpec(String currentUser, Set<String> userGroups) {
    return (root, query, cb) -> {
      List<Predicate> orPredicates = new ArrayList<>();
      if (currentUser != null && !currentUser.isBlank()) {
        orPredicates.add(cb.equal(root.get("assignedBy"), currentUser));
        orPredicates.add(candidateUserRulePredicate(root, query, cb, currentUser));
      }
      if (!userGroups.isEmpty()) {
        orPredicates.add(candidateGroupExistsPredicate(root, query, cb, userGroups));
      }
      if (orPredicates.isEmpty()) {
        return cb.disjunction();
      }
      return cb.or(orPredicates.toArray(new Predicate[0]));
    };
  }

  private Specification<TaskInstanceEntity> userVisibilityOrderSpec(String currentUser, Set<String> userGroups) {
    return (root, query, cb) -> {
      if (query.getResultType() == Long.class || query.getResultType() == long.class) {
        return cb.conjunction();
      }

      var assignedPredicate = currentUser != null && !currentUser.isBlank()
          ? cb.equal(root.get("assignedBy"), currentUser)
          : cb.disjunction();
      var candidateUserPredicate = currentUser != null && !currentUser.isBlank()
          ? candidateUserRulePredicate(root, query, cb, currentUser)
          : cb.disjunction();
      var candidateGroupPredicate = userGroups != null && !userGroups.isEmpty()
          ? candidateGroupExistsPredicate(root, query, cb, userGroups)
          : cb.disjunction();

      var visibilityRank = cb.<Integer>selectCase()
          .when(assignedPredicate, 0)
          .when(candidateUserPredicate, 1)
          .when(candidateGroupPredicate, 2)
          .otherwise(3);
      query.orderBy(cb.asc(visibilityRank), cb.desc(root.get("startedAt")));
      return cb.conjunction();
    };
  }


  private Predicate candidateUserRulePredicate(
      Root<TaskInstanceEntity> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      String user
  ) {
    if (user == null || user.isBlank()) {
      return cb.disjunction();
    }
    return candidateUserRulePredicate(root, query, cb, Set.of(user.trim()));
  }

  private Predicate candidateUserRulePredicate(
      Root<TaskInstanceEntity> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Set<String> users
  ) {
    var normalizedUsers = users.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(user -> !user.isBlank())
        .collect(Collectors.toSet());
    if (normalizedUsers.isEmpty()) {
      return cb.disjunction();
    }

    var candidateUserTask = query.subquery(UUID.class);
    Root<TaskInstanceEntity> task = candidateUserTask.from(TaskInstanceEntity.class);
    SetJoin<TaskInstanceEntity, String> candidateUsers = task.joinSet("candidateUsers");
    candidateUserTask.select(task.get("id"));
    candidateUserTask.where(
        cb.equal(task.get("id"), root.get("id")),
        candidateUsers.in(normalizedUsers)
    );
    return cb.exists(candidateUserTask);
  }

  private Predicate candidateGroupExistsPredicate(
      Root<TaskInstanceEntity> root,
      jakarta.persistence.criteria.CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Set<String> groups
  ) {
    var normalizedGroups = groups.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(group -> !group.isBlank())
        .collect(Collectors.toSet());
    if (normalizedGroups.isEmpty()) {
      return cb.disjunction();
    }

    var candidateGroupTask = query.subquery(UUID.class);
    Root<TaskInstanceEntity> task = candidateGroupTask.from(TaskInstanceEntity.class);
    SetJoin<TaskInstanceEntity, String> candidateGroups = task.joinSet("candidateGroups");
    candidateGroupTask.select(task.get("id"));
    candidateGroupTask.where(
        cb.equal(task.get("id"), root.get("id")),
        candidateGroups.in(normalizedGroups)
    );
    return cb.exists(candidateGroupTask);
  }

  private long countBySpec(Specification<TaskInstanceEntity> spec) {
    return taskInstanceEntityRepository.count(spec);
  }

  @Override
  public Optional<TaskInstance> findByExternalId(String id) {
    return taskInstanceEntityRepository.findByExternalId(id).map(taskMapper::toModel);
  }

  @Override
  public Map<String, TaskInstance> findAllByExternalIds(Collection<String> externalIds) {
    if (externalIds == null || externalIds.isEmpty()) {
      return Map.of();
    }
    return taskInstanceEntityRepository.findAllByExternalIdIn(externalIds).stream()
        .collect(Collectors.toMap(
            TaskInstanceEntity::getExternalId,
            taskMapper::toModel,
            (_, b) -> b
        ));
  }

}
