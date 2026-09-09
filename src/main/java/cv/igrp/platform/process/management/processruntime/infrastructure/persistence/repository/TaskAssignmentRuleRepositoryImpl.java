package cv.igrp.platform.process.management.processruntime.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.config.AuditMapping;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskAssignmentRuleRepository;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskAssignmentRuleEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.TaskAssignmentRuleEntityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.SetJoin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Transactional
public class TaskAssignmentRuleRepositoryImpl implements TaskAssignmentRuleRepository {

  private final TaskAssignmentRuleEntityRepository repository;
  private final EntityManager entityManager;

  public TaskAssignmentRuleRepositoryImpl(
      TaskAssignmentRuleEntityRepository repository,
      EntityManager entityManager
  ) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  public void save(TaskAssignmentRule rule) {
    repository.save(toEntity(rule));
  }

  @Override
  public TaskAssignmentRule updateAssignment(
      Identifier ruleId,
      Code assignee,
      Set<String> candidateUsers,
      Set<String> candidateGroups,
      Integer priority
  ) {
    var rule = findUpdatableRule(ruleId);
    rule.setAssignee(assignee != null ? assignee.getValue() : null);
    rule.getCandidateUsers().clear();
    rule.getCandidateUsers().addAll(normalize(candidateUsers));
    rule.getCandidateGroups().clear();
    rule.getCandidateGroups().addAll(normalize(candidateGroups));
    if (priority != null) {
      rule.setPriority(priority);
    }
    return toModel(repository.save(rule));
  }

  @Override
  public void deactivate(Identifier ruleId) {
    var rule = findUpdatableRule(ruleId);
    rule.setActive(false);
    repository.save(rule);
  }

  private TaskAssignmentRuleEntity findUpdatableRule(Identifier ruleId) {
    var rule = repository.findById(ruleId.getValue())
        .orElseThrow(() -> IgrpResponseStatusException.notFound(
            "No Task Assignment Rule found with id: " + ruleId.getValue()
        ));
    if (!Boolean.TRUE.equals(rule.getActive()) || Boolean.TRUE.equals(rule.getConsumed())) {
      throw IgrpResponseStatusException.of(
          HttpStatus.CONFLICT,
          "Task Assignment Rule can only be changed when active=true and consumed=false"
      );
    }
    return rule;
  }

  @Override
  @Transactional(readOnly = true)
  public PageableLista<TaskAssignmentRule> findAll(TaskAssignmentRuleFilter filter) {
    PageRequest pageRequest = PageRequest.of(
        filter.getPage(),
        filter.getSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );
    Page<TaskAssignmentRuleEntity> page = repository.findAll(buildSpecification(filter), pageRequest);
    return PageableLista.<TaskAssignmentRule>builder()
        .pageNumber(page.getNumber())
        .pageSize(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .last(page.isLast())
        .first(page.isFirst())
        .content(page.getContent().stream().map(this::toModel).toList())
        .build();
  }

  private Specification<TaskAssignmentRuleEntity> buildSpecification(TaskAssignmentRuleFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (filter.getProcessInstanceId() != null) {
        predicates.add(cb.equal(
            root.get("processInstanceId").get("id"),
            filter.getProcessInstanceId().getValue()
        ));
      }
      if (filter.getProcessDefinitionKey() != null) {
        predicates.add(cb.equal(
            root.get("processDefinitionKey"),
            filter.getProcessDefinitionKey().getValue()
        ));
      }
      if (filter.getTaskDefinitionKey() != null) {
        predicates.add(cb.equal(
            root.get("taskDefinitionKey"),
            filter.getTaskDefinitionKey().getValue()
        ));
      }
      if (filter.getAssignee() != null) {
        predicates.add(cb.equal(root.get("assignee"), filter.getAssignee().getValue()));
      }
      if (!filter.getCandidateUsers().isEmpty()) {
        query.distinct(true);
        SetJoin<TaskAssignmentRuleEntity, String> candidateUsers =
            root.joinSet("candidateUsers", JoinType.INNER);
        predicates.add(candidateUsers.in(normalize(filter.getCandidateUsers())));
      }
      if (!filter.getCandidateGroups().isEmpty()) {
        query.distinct(true);
        SetJoin<TaskAssignmentRuleEntity, String> candidateGroups =
            root.joinSet("candidateGroups", JoinType.INNER);
        predicates.add(candidateGroups.in(normalize(filter.getCandidateGroups())));
      }
      if (filter.getAssignmentMode() != null) {
        predicates.add(cb.equal(root.get("assignmentMode"), filter.getAssignmentMode()));
      }
      if (filter.getConsumed() != null) {
        predicates.add(cb.equal(root.get("consumed"), filter.getConsumed()));
      }
      if (filter.getActive() != null) {
        predicates.add(cb.equal(root.get("active"), filter.getActive()));
      }
      if (filter.getCreatedByTask() != null) {
        predicates.add(cb.equal(
            root.get("createdByTask").get("id"),
            filter.getCreatedByTask().getValue()
        ));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Set<String> normalize(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .collect(Collectors.toSet());
  }

  @Override
  public List<TaskAssignmentRule> findActiveByProcessInstanceAndTaskDefinition(
      Identifier processInstanceId,
      Code taskDefinitionKey
  ) {
    return repository.findAll((root, query, cb) -> cb.and(
            cb.equal(root.get("processInstanceId").get("id"), processInstanceId.getValue()),
            cb.equal(root.get("taskDefinitionKey"), taskDefinitionKey.getValue()),
            cb.isTrue(root.get("active")),
            cb.isFalse(root.get("consumed"))
        ))
        .stream()
        .map(this::toModel)
        .toList();
  }

  @Override
  public Set<String> findCandidateUsersForTask(
      Identifier taskInstanceId,
      Identifier processInstanceId,
      Code taskDefinitionKey
  ) {
    var rules = repository.findAll((root, query, cb) -> cb.and(
        cb.equal(root.get("processInstanceId").get("id"), processInstanceId.getValue()),
        cb.equal(root.get("taskDefinitionKey"), taskDefinitionKey.getValue()),
        cb.isTrue(root.get("active")),
        cb.or(
            cb.isFalse(root.get("consumed")),
            cb.equal(root.get("createdByTask").get("id"), taskInstanceId.getValue())
        )
    ));

    Set<String> users = new LinkedHashSet<>();
    rules.forEach(rule -> users.addAll(rule.getCandidateUsers()));
    return users;
  }

  @Override
  public void markConsumed(Identifier ruleId, Identifier taskInstanceId) {
    repository.findById(ruleId.getValue()).ifPresent(rule -> {
      rule.setConsumed(true);
      rule.setCreatedByTask(toTaskInstanceEntity(taskInstanceId));
      repository.save(rule);
    });
  }

  private TaskAssignmentRuleEntity toEntity(TaskAssignmentRule rule) {
    var entity = new TaskAssignmentRuleEntity();
    entity.setId(rule.getId().getValue());
    entity.setProcessDefinitionKey(rule.getProcessDefinitionKey().getValue());
    entity.setProcessInstanceId(toProcessInstanceEntity(rule));
    entity.setTaskDefinitionKey(rule.getTaskDefinitionKey().getValue());
    entity.setAssignee(rule.getAssignee() != null ? rule.getAssignee().getValue() : null);
    entity.getCandidateUsers().clear();
    entity.getCandidateUsers().addAll(rule.getCandidateUsers());
    entity.getCandidateGroups().clear();
    entity.getCandidateGroups().addAll(rule.getCandidateGroups());
    entity.setAssignmentMode(rule.getAssignmentMode());
    entity.setPriority(rule.getPriority());
    entity.setConsumed(rule.isConsumed());
    entity.setActive(rule.isActive());
    entity.setCreatedByTask(toTaskInstanceEntity(rule));
    return entity;
  }

  private TaskAssignmentRule toModel(TaskAssignmentRuleEntity entity) {
    var model = TaskAssignmentRule.builder()
        .id(Identifier.create(entity.getId()))
        .processDefinitionKey(Code.create(entity.getProcessDefinitionKey()))
        .processInstanceId(entity.getProcessInstanceId() != null
            ? Identifier.create(entity.getProcessInstanceId().getId())
            : null)
        .taskDefinitionKey(Code.create(entity.getTaskDefinitionKey()))
        .assignee(entity.getAssignee() != null ? Code.create(entity.getAssignee()) : null)
        .candidateUsers(entity.getCandidateUsers())
        .candidateGroups(entity.getCandidateGroups())
        .assignmentMode(entity.getAssignmentMode())
        .priority(entity.getPriority())
        .consumed(entity.getConsumed())
        .active(entity.getActive())
        .createdByTask(entity.getCreatedByTask() != null
            ? Identifier.create(entity.getCreatedByTask().getId())
            : null)
        .persisted(true)
        .build();
    model.setAudit(AuditMapping.trail(entity));
    return model;
  }

  private ProcessInstanceEntity toProcessInstanceEntity(TaskAssignmentRule rule) {
    if (rule.getProcessInstanceId() == null) {
      return null;
    }
    return entityManager.getReference(ProcessInstanceEntity.class, rule.getProcessInstanceId().getValue());
  }

  private TaskInstanceEntity toTaskInstanceEntity(TaskAssignmentRule rule) {
    if (rule.getCreatedByTask() == null) {
      return null;
    }
    return toTaskInstanceEntity(rule.getCreatedByTask());
  }

  private TaskInstanceEntity toTaskInstanceEntity(Identifier taskInstanceId) {
    return entityManager.getReference(TaskInstanceEntity.class, taskInstanceId.getValue());
  }
}
