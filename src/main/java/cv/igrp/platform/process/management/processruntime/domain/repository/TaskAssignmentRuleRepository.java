package cv.igrp.platform.process.management.processruntime.domain.repository;

import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRule;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleFilter;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;

import java.util.List;
import java.util.Set;

public interface TaskAssignmentRuleRepository {

  void save(TaskAssignmentRule rule);

  PageableLista<TaskAssignmentRule> findAll(TaskAssignmentRuleFilter filter);

  TaskAssignmentRule updateAssignment(
      Identifier ruleId,
      Code assignee,
      Set<String> candidateUsers,
      Set<String> candidateGroups,
      Integer priority
  );

  void deactivate(Identifier ruleId);

  List<TaskAssignmentRule> findActiveByProcessInstanceAndTaskDefinition(
      Identifier processInstanceId,
      Code taskDefinitionKey
  );

  Set<String> findCandidateUsersForTask(
      Identifier taskInstanceId,
      Identifier processInstanceId,
      Code taskDefinitionKey
  );

  void markConsumed(Identifier ruleId, Identifier taskInstanceId);

}
