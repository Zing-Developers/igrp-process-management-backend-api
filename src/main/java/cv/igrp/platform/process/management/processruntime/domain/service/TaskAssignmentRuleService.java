package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskAssignmentRuleRepository;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class TaskAssignmentRuleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskAssignmentRuleService.class);

  private final TaskAssignmentRuleRepository repository;

  public TaskAssignmentRuleService(TaskAssignmentRuleRepository repository) {
    this.repository = repository;
  }

  public PageableLista<TaskAssignmentRule> getAll(TaskAssignmentRuleFilter filter) {
    return repository.findAll(filter);
  }

  public TaskAssignmentRule updateAssignment(String id, Code assignee, Set<String> candidateUsers) {
    LOGGER.info(
        "Updating task assignment rule [{}] with assignee [{}] and candidateUsers [{}]",
        id,
        assignee != null ? assignee.getValue() : null,
        candidateUsers
    );
    var updatedRule = repository.updateAssignment(Identifier.create(id), assignee, candidateUsers);
    LOGGER.info(
        "Updated task assignment rule [{}]; active [{}], consumed [{}]",
        id,
        updatedRule.isActive(),
        updatedRule.isConsumed()
    );
    return updatedRule;
  }

  public void deactivate(String id) {
    LOGGER.info("Deactivating task assignment rule [{}]", id);
    repository.deactivate(Identifier.create(id));
    LOGGER.info("Deactivated task assignment rule [{}]", id);
  }
}
