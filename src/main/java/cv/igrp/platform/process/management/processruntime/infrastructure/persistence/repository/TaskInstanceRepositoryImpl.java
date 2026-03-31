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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
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
  public void create(TaskInstance taskInstance) {
    taskInstanceEntityRepository.save(taskMapper.toNewTaskEntity(taskInstance));
  }


  @Override
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

    PageRequest pageRequest = PageRequest.of(
        filter.getPage(),
        filter.getSize(),
        Sort.by(Sort.Direction.DESC, "startedAt")
    );

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

    Specification<TaskInstanceEntity> spec = (root, query, builder) -> null;

    spec = spec.and((root, query, cb) ->
        cb.equal(root.get("processInstanceId").get("isArchived"), filter.isArchived()));

    if (filter.getProcessInstanceId() != null) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("processInstanceId").get("id"), filter.getProcessInstanceId().getValue()));
    }

    if (filter.getProcessNumber() != null) {
      spec = spec.and((root, query, cb) ->
          cb.or(cb.equal(root.get("processInstanceId").get("number"), filter.getProcessNumber().getValue()),
              cb.equal(root.get("processInstanceId").get("businessKey"), filter.getProcessNumber().getValue())));
    }

    if (filter.getApplicationBase() != null) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("processInstanceId").get("applicationBase"), filter.getApplicationBase().getValue()));
    }

    if (filter.getName() != null) {
      spec = spec.and((root, query, cb) ->
          cb.like(root.get("name"), "%" + filter.getName().getValue() + "%"));
    }

    if (filter.getProcessName() != null) {
      spec = spec.and((root, query, cb) ->
          cb.like(root.get("processInstanceId").get("name"), "%" + filter.getProcessName().getValue() + "%"));
    }

    if (filter.getProcessRealeaseKey() != null) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("processInstanceId").get("procReleaseKey"), filter.getProcessRealeaseKey().getValue()));
    }

    if (filter.getStatus() != null) {
      spec = spec.and((root, query, cb) ->
          cb.equal(root.get("status"), filter.getStatus().getCode()));
    }

    if (filter.getDateFrom() != null) {
      spec = spec.and((root, query, cb) ->
          cb.greaterThanOrEqualTo(root.get("startedAt"), filter.getDateFrom().atStartOfDay()));
    }

    if (filter.getDateTo() != null) {
      spec = spec.and((root, query, cb) ->
          cb.lessThanOrEqualTo(root.get("startedAt"), filter.getDateTo().atTime(LocalTime.MAX)));
    }

    if (!filter.getVariablesExpressions().isEmpty()) {
      spec = spec.and((root, query, cb) -> {
        List<Predicate> predicates = filter.getVariablesExpressions()
            .stream()
            .map(expr -> buildVariablePredicate(expr, root, cb))
            .toList();
        return cb.and(predicates.toArray(new Predicate[0]));
      });
    }

    // User visibility: assigned to current user OR user is in a candidate group
    if (filter.isFilterByCurrentUser() && !filter.isSuperAdmin()) {
      String currentUser = filter.getUser() != null ? filter.getUser().getValue() : null;
      Set<String> groups = filter.getContextUserGroups();

      LOGGER.debug("Adding visibility spec for user [{}] with groups {}", currentUser, groups);

      spec = spec.and((root, query, cb) -> {
        List<Predicate> orPredicates = new ArrayList<>();

        if (currentUser != null) {
          orPredicates.add(cb.equal(root.get("assignedBy"), currentUser));
        }

        for (String group : groups) {
          orPredicates.add(cb.like(root.get("candidateGroups"), "%" + group + "%"));
        }

        if (orPredicates.isEmpty()) {
          return cb.disjunction();
        }
        return cb.or(orPredicates.toArray(new Predicate[0]));
      });
    }

    // Client-supplied candidate groups filter
    if (filter.getCandidateGroups() != null && !filter.getCandidateGroups().isEmpty()) {
      spec = spec.and((root, query, cb) -> {
        List<Predicate> groupPredicates = filter.getCandidateGroups().stream()
            .map(g -> cb.like(root.get("candidateGroups"), "%" + g + "%"))
            .toList();
        return cb.or(groupPredicates.toArray(new Predicate[0]));
      });
    }

    return spec;
  }

  /**
   * A public task has NO assignee and NO candidate groups — available for anyone to claim.
   */
//  private boolean isPublicTask(TaskInstanceEntity t) {
//    return (t.getAssignedBy() == null || t.getAssignedBy().isBlank())
//        && (t.getCandidateGroups() == null || t.getCandidateGroups().isBlank());
//  }

  private Set<String> splitGroups(String groups) {
    if (groups == null || groups.isBlank()) return Set.of();
    return Arrays.stream(groups.split(","))
        .map(String::trim)
        .collect(Collectors.toSet());
  }

  private boolean userCanSeeTask(TaskInstanceEntity t, String currentUser, Set<String> userGroups) {
    // 1. Task is assigned to current user
    if (Objects.equals(t.getAssignedBy(), currentUser)) {
      return true;
    }
    // 2. Task has candidate groups — user must be in one of them
    Set<String> taskGroups = splitGroups(t.getCandidateGroups());
    if (!taskGroups.isEmpty()) {
      return taskGroups.stream().anyMatch(userGroups::contains);
    }
    // 3. Unassigned task with no candidate groups — not relevant to this user
    return false;
  }

//  private boolean matchesClientFilter(TaskInstanceEntity t, Set<String> filterGroups) {
//    if (filterGroups == null || filterGroups.isEmpty()) {
//      return true;
//    }
//    if (isPublicTask(t)) {
//      return false;
//    }
//    Set<String> taskGroups = splitGroups(t.getCandidateGroups());
//    return taskGroups.stream().anyMatch(filterGroups::contains);
//  }


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

    long available = countByStatus(TaskInstanceStatus.CREATED);
    long assigned = countByStatus(TaskInstanceStatus.ASSIGNED);
    long suspended = countByStatus(TaskInstanceStatus.SUSPENDED);
    long completed = countByStatus(TaskInstanceStatus.COMPLETED);
    long canceled = countByStatus(TaskInstanceStatus.CANCELED);

    return TaskStatistics.builder()
        .totalTaskInstances(total)
        .totalAvailableTasks(available)
        .totalAssignedTasks(assigned)
        .totalSuspendedTasks(suspended)
        .totalCompletedTasks(completed)
        .totalCanceledTasks(canceled)
        .build();
  }


  private long countByStatus(TaskInstanceStatus status) {
    return taskInstanceEntityRepository.count((root, query, cb) -> cb.equal(root.get("status"), status));
  }


  @Override
  public TaskStatistics getTaskStatisticsByUser(Code user, List<String> groups, boolean isSuperAdmin) {

    Set<String> userGroups = groups == null
        ? Set.of()
        : groups.stream().map(String::trim).collect(Collectors.toSet());

    List<TaskInstanceEntity> tasks =
        taskInstanceEntityRepository.findAll();

    List<TaskInstanceEntity> visibleTasks = tasks.stream()
        .filter(t -> isSuperAdmin || userCanSeeTask(t, user.getValue(), userGroups))
        .toList();

    long total = tasks.size();

    long available = visibleTasks.stream()
        .filter(t -> t.getStatus() == TaskInstanceStatus.CREATED)
        .count();

    long assigned = visibleTasks.stream()
        .filter(t -> t.getStatus() == TaskInstanceStatus.ASSIGNED)
        .filter(t -> Objects.equals(t.getAssignedBy(), user.getValue()))
        .count();

    long suspended = visibleTasks.stream()
        .filter(t -> t.getStatus() == TaskInstanceStatus.SUSPENDED)
        .filter(t -> Objects.equals(t.getAssignedBy(), user.getValue()))
        .count();

    long completed = visibleTasks.stream()
        .filter(t -> t.getStatus() == TaskInstanceStatus.COMPLETED)
        .filter(t -> Objects.equals(t.getEndedBy(), user.getValue()))
        .count();

    long canceled = visibleTasks.stream()
        .filter(t -> t.getStatus() == TaskInstanceStatus.CANCELED)
        .filter(t -> Objects.equals(t.getEndedBy(), user.getValue()))
        .count();

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
  public Optional<TaskInstance> findByExternalId(String id) {
    return taskInstanceEntityRepository.findByExternalId(id).map(taskMapper::toModel);
  }

}
