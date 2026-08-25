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
  private final Integer priority;
  private final LocalDate dateFrom;
  private final LocalDate dateTo;
  private final Integer page;
  private final Integer size;
  private Code user;
  private List<VariablesExpression> variablesExpressions;
  private List<String> engineProcessNumbers;

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
      Integer priority,
      LocalDate dateFrom,
      LocalDate dateTo,
      Integer page,
      Integer size,
      List<VariablesExpression> variablesExpressions,
      List<String> engineProcessNumbers,
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
    this.priority = priority;
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
    this.page = page == null ? 0 : page;
    this.size = size == null ? 50 : size;
    this.variablesExpressions = variablesExpressions ==  null ? new ArrayList<>() : variablesExpressions;
    this.engineProcessNumbers = engineProcessNumbers == null ? new ArrayList<>() : engineProcessNumbers;
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

  public void includeEngineProcessNumber(String engineProcessNumber){
    this.engineProcessNumbers.add(engineProcessNumber);
  }

  public void bindCurrentUser(Code user, boolean isSuperAdmin){
    this.user = user;
    this.isSuperAdmin = isSuperAdmin;
  }

  /**
   * Discards the identity filters supplied by the client and scopes the search to the given user and
   * their groups.
   *
   * <p>Applied to callers without permission to search beyond their own work, so that
   * {@code user}, {@code candidateUsers} and {@code candidateGroups} sent in the request body cannot
   * widen visibility.
   *
   * @param user   the authenticated user
   * @param groups the authenticated user's groups
   */
  public void restrictToCurrentUser(Code user, List<String> groups){
    this.candidateUsers = new HashSet<>();
    this.candidateGroups = new HashSet<>();
    this.contextUserGroups = new HashSet<>();
    this.isSuperAdmin = false;
    this.user = user;

    if (groups != null) {
      this.contextUserGroups.addAll(groups);
    }
  }

}
