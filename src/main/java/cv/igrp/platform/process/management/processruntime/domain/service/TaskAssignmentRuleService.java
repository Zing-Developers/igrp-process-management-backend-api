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

  public TaskAssignmentRule updateAssignment(
      String id,
      Code assignee,
      Set<String> candidateUsers,
      Set<String> candidateGroups
  ) {
    LOGGER.debug(
        "Updating task assignment rule [{}] (hasAssignee [{}], candidateUsers [{}], candidateGroups [{}])",
        id,
        assignee != null,
        candidateUsers != null ? candidateUsers.size() : 0,
        candidateGroups != null ? candidateGroups.size() : 0
    );
    // Priority is left untouched (null) on manual rule updates; it is only set by automated
    // assignment delegates that resolve it from an external source.
    var updatedRule = repository.updateAssignment(Identifier.create(id), assignee, candidateUsers, candidateGroups, null);
    LOGGER.info(
        "Updated task assignment rule [{}]; active [{}], consumed [{}]",
        id,
        updatedRule.isActive(),
        updatedRule.isConsumed()
    );
    return updatedRule;
  }

  public void deactivate(String id) {
    LOGGER.debug("Deactivating task assignment rule [{}]", id);
    repository.deactivate(Identifier.create(id));
    LOGGER.info("Deactivated task assignment rule [{}]", id);
  }
}
