package cv.igrp.platform.process.management.shared.jobs;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessInstanceEntityRepository;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.DeadLetterJobQuery;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.Job;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailedJobServiceTest {

  private final ManagementService management = mock(ManagementService.class);
  private final RuntimeService runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
  private final ProcessInstanceEntityRepository processes = mock(ProcessInstanceEntityRepository.class);
  private final JobFailureRecorder failures = mock(JobFailureRecorder.class);
  private final FailedJobService service = new FailedJobService(management, runtime, mock(org.activiti.engine.RepositoryService.class), processes, failures, 3);

  private DeadLetterJobQuery deadLetters(long count, List<Job> page) {
    var query = mock(DeadLetterJobQuery.class, RETURNS_SELF);
    when(query.count()).thenReturn(count);
    when(query.listPage(anyInt(), anyInt())).thenReturn(page);
    when(management.createDeadLetterJobQuery()).thenReturn(query);
    return query;
  }

  @Test
  void listMapsEngineJobsToTheAppProcessAndActivity() {
    var job = mock(Job.class);
    when(job.getId()).thenReturn("job-1");
    when(job.getProcessInstanceId()).thenReturn("eng-1");
    when(job.getExecutionId()).thenReturn("exec-1");
    when(job.getExceptionMessage()).thenReturn("notify: HTTP 503");
    deadLetters(41, List.of(job));
    var process = new ProcessInstanceEntity();
    process.setId(UUID.randomUUID());
    process.setNumber("P-0007");
    process.setEngineProcessNumber("eng-1");
    process.setName("Pedido de certidão");
    var lastFailed = java.time.Instant.parse("2026-10-14T10:42:00Z");
    when(failures.findByExecutionIds(List.of("exec-1")))
        .thenReturn(java.util.Map.of("exec-1", new JobFailureRecorder.Failure(3, lastFailed.minusSeconds(120), lastFailed)));
    // shown in Cape Verde time (UTC-1), like the rest of the timeline
    var lastFailedAt = java.time.LocalDateTime.of(2026, 10, 14, 9, 42);
    when(processes.findAllByEngineProcessNumberIn(any())).thenReturn(List.of(process));
    var execution = mock(Execution.class);
    when(execution.getActivityId()).thenReturn("notifyIrn");
    when(runtime.createExecutionQuery().executionId("exec-1").singleResult()).thenReturn(execution);

    var page = service.list(null, 2, 20);

    assertThat(page.getTotalElements()).isEqualTo(41);
    assertThat(page.getTotalPages()).isEqualTo(3);
    assertThat(page.isLast()).isTrue();
    var row = page.getContent().get(0);
    assertThat(row.getJobId()).isEqualTo("job-1");
    assertThat(row.getProcessInstanceId()).isEqualTo(process.getId());
    assertThat(row.getProcessNumber()).isEqualTo("P-0007");
    assertThat(row.getActivityId()).isEqualTo("notifyIrn");
    assertThat(row.getExceptionMessage()).isEqualTo("notify: HTTP 503");
    assertThat(row.getProcessName()).isEqualTo("Pedido de certidão");
    assertThat(row.getExecutionId()).isEqualTo("exec-1");
    assertThat(row.getAttempts()).isEqualTo(3);
    assertThat(row.getLastFailedAt()).isEqualTo(lastFailedAt);
  }

  @Test
  void retryUsesTheConfiguredDefaultAndRejectsUnknownJobsAndBadCounts() {
    var query = deadLetters(1, List.of());
    service.retry("job-1", null, "ops@irn.mj.pt");
    verify(management).moveDeadLetterJobToExecutableJob("job-1", 3);

    assertThatThrownBy(() -> service.retry("job-1", 0, "ops@irn.mj.pt")).isInstanceOf(IllegalArgumentException.class);

    when(query.count()).thenReturn(0L);
    assertThatThrownBy(() -> service.retry("gone", 2, "ops@irn.mj.pt")).isInstanceOf(IllegalArgumentException.class);
    verify(management, never()).moveDeadLetterJobToExecutableJob("gone", 2);
  }

}
