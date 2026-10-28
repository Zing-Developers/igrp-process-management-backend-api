package cv.igrp.platform.process.management.shared.jobs;

import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent.ArtifactStatus;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent.ArtifactType;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessArtifactEvent.JobFailure;
import cv.igrp.platform.process.management.shared.application.dto.FailedJobDTO;
import cv.igrp.platform.process.management.shared.application.dto.FailedJobListPageDTO;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessInstanceEntityRepository;
import org.activiti.bpmn.model.*;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cv.igrp.platform.process.management.shared.util.DateUtil.toLocalDateTime;

/**
 * Dead-letter jobs: async jobs (webhooks and any other async service task) whose retries ran out.
 * Lists them with the app's process ids, moves them back to the executable queue on demand, and
 * gives the activity list the activities whose job failed (the engine's history leaves them out).
 */
@Service
public class FailedJobService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FailedJobService.class);

  private static final int PAGE_SIZE_DEFAULT = 20;
  private static final int PAGE_SIZE_MAX = 100;

  private final ManagementService managementService;
  private final RuntimeService runtimeService;
  private final RepositoryService repositoryService;
  private final ProcessInstanceEntityRepository processInstanceRepository;
  private final JobFailureRecorder failures;
  private final int defaultRetries;

  public FailedJobService(ManagementService managementService, RuntimeService runtimeService,
                          RepositoryService repositoryService,
                          ProcessInstanceEntityRepository processInstanceRepository,
                          JobFailureRecorder failures,
                          @Value("${igrp.async-job.retries:3}") int defaultRetries) {
    this.managementService = managementService;
    this.runtimeService = runtimeService;
    this.repositoryService = repositoryService;
    this.processInstanceRepository = processInstanceRepository;
    this.failures = failures;
    this.defaultRetries = defaultRetries;
  }

  /**
   * One page, oldest due first; {@code processInstanceId} (the app's UUID) or {@code businessKey}
   * narrows to one process.
   */
  public FailedJobListPageDTO list(UUID processInstanceId, String businessKey, Integer page, Integer size) {
    final var pageable = PageRequest.of(page == null || page < 0 ? 0 : page,
        size == null || size < 1 ? PAGE_SIZE_DEFAULT : Math.min(size, PAGE_SIZE_MAX));

    final var query = managementService.createDeadLetterJobQuery();
    final var process = processFilter(processInstanceId, businessKey);
    if (process != null) {
      query.processInstanceId(process.getEngineProcessNumber());
    }
    final long total = query.count();
    final List<Job> jobs = query.orderByJobDuedate().asc().listPage((int) pageable.getOffset(), pageable.getPageSize());

    final Map<String, ProcessInstanceEntity> processes = processInstanceRepository
        .findAllByEngineProcessNumberIn(jobs.stream().map(Job::getProcessInstanceId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(ProcessInstanceEntity::getEngineProcessNumber, Function.identity(), (a, b) -> a));
    final var recorded = failures.findByExecutionIds(executionIds(jobs.stream()));

    // ponytail: one execution lookup per row for the activity id; fine at <=100 rows per page
    final var result = new PageImpl<>(jobs.stream()
        .map(job -> toDto(job, processes.get(job.getProcessInstanceId()), failureOf(job, recorded),
            recorded.containsKey(job.getExecutionId()) ? recorded.get(job.getExecutionId()).lastReprocessedBy() : null))
        .toList(), pageable, total);

    final var dto = new FailedJobListPageDTO();
    dto.setContent(result.getContent());
    dto.setPageNumber(result.getNumber());
    dto.setPageSize(result.getSize());
    dto.setTotalElements(result.getTotalElements());
    dto.setTotalPages(result.getTotalPages());
    dto.setFirst(result.isFirst());
    dto.setLast(result.isLast());
    return dto;
  }

  /** The process both filters point at, or null with neither; giving both for different processes is an error. */
  private ProcessInstanceEntity processFilter(UUID processInstanceId, String businessKey) {
    final var byId = processInstanceId == null ? null : processInstanceRepository.findById(processInstanceId)
        .orElseThrow(() -> new IllegalArgumentException("process instance not found: " + processInstanceId));
    final var byKey = businessKey == null || businessKey.isBlank() ? null : processInstanceRepository.findByBusinessKey(businessKey)
        .orElseThrow(() -> new IllegalArgumentException("process instance not found for businessKey: " + businessKey));
    if (byId != null && byKey != null && !byId.getId().equals(byKey.getId())) {
      throw new IllegalArgumentException("processInstanceId and businessKey refer to different processes");
    }
    return byId != null ? byId : byKey;
  }

  public String stacktrace(String jobId) {
    requireDeadLetter(jobId);
    return managementService.getDeadLetterJobExceptionStacktrace(jobId);
  }

  /**
   * Back to the executable queue with {@code retries} attempts (default IGRP_ASYNC_JOB_RETRIES); runs on
   * the next acquisition. Counted as a reprocess; the attempts it uses add to the job's total.
   */
  public void retry(String jobId, Integer retries, String requestedBy) {
    final Job job = requireDeadLetter(jobId);
    final int attempts = retries == null ? defaultRetries : retries;
    if (attempts < 1 || attempts > 100) {
      throw new IllegalArgumentException("retries must be between 1 and 100");
    }
    // before the move: once moved, the job executor can run (and fail) it before this line returns
    if (job.getExecutionId() != null) {
      failures.recordReprocess(job.getExecutionId(), requestedBy);
    }
    managementService.moveDeadLetterJobToExecutableJob(jobId, attempts);
    LOGGER.info("Dead-letter job {} moved back to the executable queue with {} retries by {}", jobId, attempts, requestedBy);
  }

  /**
   * The activities of one engine process whose job failed: out of retries (FAILED), waiting for the
   * next attempt (RETRYING) or put back on the queue by hand (REPROCESSING). The engine's history
   * leaves them out, since a failed job rolls back, so the activity list adds them from here, each
   * with its error and what to reprocess.
   */
  public List<ProcessArtifactEvent> stuckActivities(String engineProcessInstanceId) {
    final List<Job> deadLetters = managementService.createDeadLetterJobQuery().processInstanceId(engineProcessInstanceId).list();
    final List<Job> retrying = managementService.createTimerJobQuery().processInstanceId(engineProcessInstanceId).withException().list();
    // an executable job that carries an exception has failed before: reprocessed, or a retry whose wait is over
    final List<Job> queued = managementService.createJobQuery().processInstanceId(engineProcessInstanceId).withException().list();
    final var recorded = failures.findByExecutionIds(executionIds(Stream.of(deadLetters, retrying, queued).flatMap(List::stream)));
    return Stream.of(
            deadLetters.stream().map(job -> stuckActivity(job, ArtifactStatus.FAILED, failureOf(job, recorded))),
            retrying.stream().map(job -> stuckActivity(job, ArtifactStatus.RETRYING, failureOf(job, recorded))),
            queued.stream().map(job -> {
              final var failure = recorded.get(job.getExecutionId());
              final var status = failure != null && failure.reprocessPending() ? ArtifactStatus.REPROCESSING : ArtifactStatus.RETRYING;
              return stuckActivity(job, status, failureOf(job, recorded));
            }))
        .flatMap(Function.identity())
        .toList();
  }

  private ProcessArtifactEvent stuckActivity(Job job, ArtifactStatus status, JobFailure failure) {
    final String activityId = activityIdOf(job);
    final FlowElement element = activityId == null ? null
        : repositoryService.getBpmnModel(job.getProcessDefinitionId()).getFlowElement(activityId);
    return ProcessArtifactEvent.builder()
        .processInstanceId(job.getProcessInstanceId())
        .artifactId(activityId)
        .artifactName(element != null && element.getName() != null ? element.getName() : activityId)
        .type(artifactType(element))
        .executionId(job.getExecutionId())
        .status(status)
        .startTime(failure.firstFailedAt())
        .failure(failure)
        .build();
  }

  /** The same mapping as the framework's timeline, so a failed activity shows the type its history row would. */
  private static ArtifactType artifactType(FlowElement element) {
    if (element instanceof UserTask) return ArtifactType.USER_TASK;
    if (element instanceof ServiceTask) return ArtifactType.SERVICE_TASK;
    if (element instanceof ScriptTask) return ArtifactType.SCRIPT_TASK;
    if (element instanceof ManualTask) return ArtifactType.MANUAL_TASK;
    if (element instanceof ReceiveTask) return ArtifactType.RECEIVE_TASK;
    if (element instanceof SendTask) return ArtifactType.SEND_TASK;
    if (element instanceof BusinessRuleTask) return ArtifactType.BUSINESS_RULE_TASK;
    if (element instanceof ExclusiveGateway) return ArtifactType.EXCLUSIVE_GATEWAY;
    if (element instanceof ParallelGateway) return ArtifactType.PARALLEL_GATEWAY;
    if (element instanceof InclusiveGateway) return ArtifactType.INCLUSIVE_GATEWAY;
    if (element instanceof IntermediateCatchEvent) return ArtifactType.MESSAGE_INTERMEDIATE_EVENT_CATCH;
    if (element instanceof CallActivity) return ArtifactType.CALL_ACTIVITY;
    if (element instanceof SubProcess) return ArtifactType.SUB_PROCESS;
    return ArtifactType.OTHER;
  }

  private JobFailure failureOf(Job job, Map<String, JobFailureRecorder.Failure> recorded) {
    final var failure = recorded.get(job.getExecutionId());
    return new JobFailure(job.getId(), job.getExceptionMessage(),
        failure != null ? failure.attempts() : null,
        failure != null ? failure.firstFailedAt() : null,
        failure != null ? failure.lastFailedAt() : null,
        failure != null ? failure.reprocessCount() : null,
        failure != null ? failure.lastReprocessedAt() : null);
  }

  private static List<String> executionIds(Stream<Job> jobs) {
    return jobs.map(Job::getExecutionId).filter(Objects::nonNull).toList();
  }

  private String activityIdOf(Job job) {
    if (job.getExecutionId() == null) {
      return null;
    }
    final Execution execution = runtimeService.createExecutionQuery().executionId(job.getExecutionId()).singleResult();
    return execution != null ? execution.getActivityId() : null;
  }

  private Job requireDeadLetter(String jobId) {
    final Job job = managementService.createDeadLetterJobQuery().jobId(jobId).singleResult();
    if (job == null) {
      throw new IllegalArgumentException("failed job not found: " + jobId);
    }
    return job;
  }

  private FailedJobDTO toDto(Job job, ProcessInstanceEntity process, JobFailure failure, String lastReprocessedBy) {
    return new FailedJobDTO(
        job.getId(),
        process != null ? process.getId() : null,
        process != null ? process.getNumber() : null,
        process != null ? process.getName() : null,
        job.getProcessInstanceId(),
        job.getProcessDefinitionId(),
        job.getExecutionId(),
        activityIdOf(job),
        job.getExceptionMessage(),
        failure.attempts(),
        toLocalDateTime(failure.firstFailedAt()),
        toLocalDateTime(failure.lastFailedAt()),
        failure.reprocessCount(),
        toLocalDateTime(failure.lastReprocessedAt()),
        lastReprocessedBy);
  }

}
