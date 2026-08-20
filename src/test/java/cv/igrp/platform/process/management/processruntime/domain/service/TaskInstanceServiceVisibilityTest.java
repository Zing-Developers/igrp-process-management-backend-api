package cv.igrp.platform.process.management.processruntime.domain.service;

import cv.igrp.platform.process.management.processdefinition.domain.repository.ProcessDefinitionRepository;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstance;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskInstanceFilter;
import cv.igrp.platform.process.management.processruntime.domain.repository.ProcessInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.RuntimeProcessEngineRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskAssignmentRuleRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskInstanceEventRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.TaskInstanceRepository;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import cv.igrp.platform.process.management.shared.domain.models.PageableLista;
import cv.igrp.platform.process.management.shared.security.util.IgrpAuthorizationConstants;
import cv.igrp.platform.process.management.shared.security.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Task search must never widen a caller's visibility just because the request body asks it to.
 *
 * <p>Both {@code POST /tasks-instances/search} and {@code POST /tasks-instances/me} go through
 * {@link TaskInstanceService#getAllTaskInstances}, so this is the single place the rule is enforced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskInstanceServiceVisibilityTest {

  private static final String CURRENT_USER = "eu@irn.mj.pt";
  private static final List<String> CURRENT_GROUPS = List.of("MEU_GRUPO");

  @Mock private TaskInstanceRepository taskInstanceRepository;
  @Mock private TaskInstanceEventRepository taskInstanceEventRepository;
  @Mock private TaskAssignmentRuleRepository taskAssignmentRuleRepository;
  @Mock private RuntimeProcessEngineRepository runtimeProcessEngineRepository;
  @Mock private ProcessInstanceRepository processInstanceRepository;
  @Mock private ProcessDefinitionRepository processDefinitionRepository;
  @Mock private UserProfileRepository userProfileRepository;
  @Mock private UserContext userContext;

  private TaskInstanceService service;

  @BeforeEach
  void setUp() {
    service = new TaskInstanceService(
        taskInstanceRepository, taskInstanceEventRepository, taskAssignmentRuleRepository,
        runtimeProcessEngineRepository, processInstanceRepository, processDefinitionRepository,
        userProfileRepository, userContext);

    when(userContext.getCurrentUser()).thenReturn(Code.create(CURRENT_USER));
    when(userContext.getCurrentGroups()).thenReturn(CURRENT_GROUPS);
    when(taskInstanceRepository.findAll(any()))
        .thenReturn(PageableLista.<TaskInstance>builder()
            .pageNumber(0).pageSize(20).totalElements(0L).totalPages(0)
            .first(true).last(true).content(List.of())
            .build());
    when(runtimeProcessEngineRepository.getProcessVariablesBatch(any())).thenReturn(Map.of());
  }

  /** A filter as a malicious client would send it: someone else's tasks, current-user scoping off. */
  private static TaskInstanceFilter filterAskingForEveryoneElsesTasks() {
    return TaskInstanceFilter.builder()
        .user(Code.create("outra.pessoa@irn.mj.pt"))
        .candidateGroups(Set.of("OUTRO_GRUPO"))
        .candidateUsers(Set.of("outra.pessoa@irn.mj.pt"))
        .filterByCurrentUser(false)
        .page(0)
        .size(20)
        .build();
  }

  private TaskInstanceFilter filterReachingTheRepository() {
    var captor = ArgumentCaptor.forClass(TaskInstanceFilter.class);
    org.mockito.Mockito.verify(taskInstanceRepository).findAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("without the search-all permission, the client's identity filters are discarded")
  void restrictsToTheCurrentUserWhenThePermissionIsMissing() {

    when(userContext.isSuperAdmin()).thenReturn(false);
    when(userContext.hasPermission(IgrpAuthorizationConstants.TASK_INSTANCES_SEARCH_ALL)).thenReturn(false);

    service.getAllTaskInstances(filterAskingForEveryoneElsesTasks());

    var applied = filterReachingTheRepository();

    assertThat(applied.getUser().getValue()).isEqualTo(CURRENT_USER);
    assertThat(applied.getCandidateGroups()).isEmpty();
    assertThat(applied.getCandidateUsers()).isEmpty();
    assertThat(applied.getContextUserGroups()).containsExactlyElementsOf(CURRENT_GROUPS);
    assertThat(applied.isSuperAdmin()).isFalse();
  }

  @Test
  @DisplayName("with the search-all permission, the filter is left as the client sent it")
  void leavesTheFilterAloneWhenThePermissionIsPresent() {

    when(userContext.isSuperAdmin()).thenReturn(false);
    when(userContext.hasPermission(IgrpAuthorizationConstants.TASK_INSTANCES_SEARCH_ALL)).thenReturn(true);

    service.getAllTaskInstances(filterAskingForEveryoneElsesTasks());

    var applied = filterReachingTheRepository();

    assertThat(applied.getUser().getValue()).isEqualTo("outra.pessoa@irn.mj.pt");
    assertThat(applied.getCandidateGroups()).containsExactly("OUTRO_GRUPO");
  }

  @Test
  @DisplayName("a super admin searches everything without needing the permission")
  void leavesTheFilterAloneForASuperAdmin() {

    when(userContext.isSuperAdmin()).thenReturn(true);
    when(userContext.hasPermission(IgrpAuthorizationConstants.TASK_INSTANCES_SEARCH_ALL)).thenReturn(false);

    service.getAllTaskInstances(filterAskingForEveryoneElsesTasks());

    var applied = filterReachingTheRepository();

    assertThat(applied.getUser().getValue()).isEqualTo("outra.pessoa@irn.mj.pt");
    assertThat(applied.getCandidateGroups()).containsExactly("OUTRO_GRUPO");
  }

  @Test
  @DisplayName("filterByCurrentUser still narrows the search for a caller allowed to search all")
  void honoursFilterByCurrentUserForACallerAllowedToSearchAll() {

    when(userContext.isSuperAdmin()).thenReturn(false);
    when(userContext.hasPermission(IgrpAuthorizationConstants.TASK_INSTANCES_SEARCH_ALL)).thenReturn(true);

    service.getAllTaskInstances(TaskInstanceFilter.builder()
        .filterByCurrentUser(true)
        .page(0)
        .size(20)
        .build());

    var applied = filterReachingTheRepository();

    assertThat(applied.getUser().getValue()).isEqualTo(CURRENT_USER);
    assertThat(applied.getContextUserGroups()).containsExactlyElementsOf(CURRENT_GROUPS);
  }

}
