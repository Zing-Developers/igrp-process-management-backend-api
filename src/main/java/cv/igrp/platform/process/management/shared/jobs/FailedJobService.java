package cv.igrp.platform.process.management.shared.jobs;

import cv.igrp.platform.process.management.shared.application.dto.FailedJobDTO;
import cv.igrp.platform.process.management.shared.application.dto.FailedJobListPageDTO;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessInstanceEntityRepository;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dead-letter jobs: async jobs (webhooks and any other async service task) whose retries ran out.
 * Lists them with the app's process ids and moves them back to the executable queue on demand.
 */
@Service
public class FailedJobService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FailedJobService.class);

  private static final int PAGE_SIZE_DEFAULT = 20;
  private static final int PAGE_SIZE_MAX = 100;

  private final ManagementService managementService;
  private final RuntimeService runtimeService;
  private final ProcessInstanceEntityRepository processInstanceRepository;
  private final JobFailureRecorder failures;
  private final int defaultRetries;

  public FailedJobService(ManagementService managementService, RuntimeService runtimeService,
                          ProcessInstanceEntityRepository processInstanceRepository,
                          JobFailureRecorder failures,
                          @Value("${igrp.async-job.retries:3}") int defaultRetries) {
    this.managementService = managementService;
    this.runtimeService = runtimeService;
    this.processInstanceRepository = processInstanceRepository;
    this.failures = failures;
    this.defaultRetries = defaultRetries;
  }

  /** One page, oldest due first; {@code processInstanceId} (the app's UUID) narrows to one process. */
  public FailedJobListPageDTO list(UUID processInstanceId, Integer page, Integer size) {
    final int pageNumber = page == null || page < 0 ? 0 : page;
    final int pageSize = size == null || size < 1 ? PAGE_SIZE_DEFAULT : Math.min(size, PAGE_SIZE_MAX);

    final var query = managementService.createDeadLetterJobQuery();
    if (processInstanceId != null) {
      final var process = processInstanceRepository.findById(processInstanceId)
          .orElseThrow(() -> new IllegalArgumentException("process instance not found: " + processInstanceId));
      query.processInstanceId(process.getEngineProcessNumber());
    }
    final long total = query.count();
    final var jobs = query.orderByJobDuedate().asc().listPage(pageNumber * pageSize, pageSize);

    final Map<String, ProcessInstanceEntity> processes = processInstanceRepository
        .findAllByEngineProcessNumberIn(jobs.stream().map(Job::getProcessInstanceId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(ProcessInstanceEntity::getEngineProcessNumber, Function.identity(), (a, b) -> a));

    final var recorded = failures.findByExecutionIds(
        jobs.stream().map(Job::getExecutionId).filter(java.util.Objects::nonNull).toList());

    final var dto = new FailedJobListPageDTO();
    // ponytail: one execution lookup per row for the activity id; fine at <=100 rows per page
    dto.setContent(jobs.stream()
        .map(job -> toDto(job, processes.get(job.getProcessInstanceId()), recorded.get(job.getExecutionId())))
        .toList());
    dto.setPageNumber(pageNumber);
    dto.setPageSize(pageSize);
    dto.setTotalElements(total);
    dto.setTotalPages((int) ((total + pageSize - 1) / pageSize));
    dto.setFirst(pageNumber == 0);
    dto.setLast((long) (pageNumber + 1) * pageSize >= total);
    return dto;
  }

  public String stacktrace(String jobId) {
    requireDeadLetter(jobId);
    return managementService.getDeadLetterJobExceptionStacktrace(jobId);
  }

  /** Back to the executable queue with {@code retries} attempts (default IGRP_ASYNC_JOB_RETRIES); runs on the next acquisition. */
  public void retry(String jobId, Integer retries, String requestedBy) {
    requireDeadLetter(jobId);
    final int attempts = retries == null ? defaultRetries : retries;
    if (attempts < 1 || attempts > 100) {
      throw new IllegalArgumentException("retries must be between 1 and 100");
    }
    managementService.moveDeadLetterJobToExecutableJob(jobId, attempts);
    LOGGER.info("Dead-letter job {} moved back to the executable queue with {} retries by {}", jobId, attempts, requestedBy);
  }

  private void requireDeadLetter(String jobId) {
    if (managementService.createDeadLetterJobQuery().jobId(jobId).count() == 0) {
      throw new IllegalArgumentException("failed job not found: " + jobId);
    }
  }

  private FailedJobDTO toDto(Job job, ProcessInstanceEntity process, JobFailureRecorder.Failure failure) {
    final Execution execution = job.getExecutionId() == null ? null
        : runtimeService.createExecutionQuery().executionId(job.getExecutionId()).singleResult();
    return new FailedJobDTO(
        job.getId(),
        process != null ? process.getId() : null,
        process != null ? process.getNumber() : null,
        process != null ? process.getName() : null,
        job.getProcessInstanceId(),
        job.getProcessDefinitionId(),
        job.getExecutionId(),
        execution != null ? execution.getActivityId() : null,
        job.getExceptionMessage(),
        failure != null ? failure.attempts() : null,
        failure != null ? failure.firstFailedAt() : null,
        failure != null ? failure.lastFailedAt() : null);
  }

}
