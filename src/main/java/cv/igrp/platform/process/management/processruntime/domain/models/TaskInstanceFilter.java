package cv.igrp.platform.process.management.processruntime.domain.models;

import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Getter
public class TaskInstanceFilter {

  private final Identifier processInstanceId;
  private final Code processNumber;
  private final Code applicationBase;
  private final Name processName;
  private final TaskInstanceStatus status;
  private final LocalDate dateFrom;
  private final LocalDate dateTo;
  private final Integer page;
  private final Integer size;
  private Code user;
  private List<VariablesExpression> variablesExpressions;

  private Set<String> candidateGroups;
  private Set<String> candidateUsers;
  private Set<String> contextUserGroups;

  private final Name name;
  private final Code processRealeaseKey;

  private final boolean filterByCurrentUser;

  private boolean isSuperAdmin;

  private boolean isArchived;

  @Builder
  private TaskInstanceFilter(
      Identifier processInstanceId,
      Code processNumber,
      Code applicationBase,
      Name processName,
      Set<String> candidateGroups,
      Set<String> candidateUsers,
      Code user,
      TaskInstanceStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      Integer page,
      Integer size,
      List<VariablesExpression> variablesExpressions,
      Name name,
      Code processReleaseKey,
      boolean filterByCurrentUser,
      Set<String> contextUserGroups,
      boolean isSuperAdmin,
      boolean isArchived
  ) {
    this.processInstanceId = processInstanceId;
    this.applicationBase = applicationBase;
    this.processNumber = processNumber;
    this.processName = processName;
    this.candidateGroups = candidateGroups;
    this.candidateUsers = candidateUsers == null ? new HashSet<>() : candidateUsers;
    this.user = user;
    this.status = status;
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
    this.page = page == null ? 0 : page;
    this.size = size == null ? 50 : size;
    this.variablesExpressions = variablesExpressions ==  null ? new ArrayList<>() : variablesExpressions;
    this.candidateGroups = candidateGroups == null ? new HashSet<>() : candidateGroups;
    this.name = name;
    this.processRealeaseKey = processReleaseKey;
    this.filterByCurrentUser = filterByCurrentUser;
    this.contextUserGroups = contextUserGroups == null ? new HashSet<>() : contextUserGroups;
    this.isSuperAdmin = isSuperAdmin;
    this.isArchived = isArchived;
  }

  public void addContextUserGroup(String group){
    this.contextUserGroups.add(group);
  }

  public void bindCurrentUser(Code user, boolean isSuperAdmin){
    this.user = user;
    this.isSuperAdmin = isSuperAdmin;
  }

}
