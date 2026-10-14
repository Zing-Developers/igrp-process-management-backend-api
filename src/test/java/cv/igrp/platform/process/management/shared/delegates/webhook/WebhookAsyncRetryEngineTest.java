package cv.igrp.platform.process.management.shared.delegates.webhook;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessInstanceEntityRepository;
import cv.igrp.platform.process.management.shared.jobs.FailedJobService;
import cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.activiti.engine.runtime.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The real engine (H2, job executor driven by hand): an async service task that keeps getting 503
 * uses up its retries, lands in the dead letter, shows up in FailedJobService and, once the
 * endpoint answers, a reprocess finishes the process with the outcome recorded.
 */
class WebhookAsyncRetryEngineTest {

  /** Stands in for a webhook: replies with whatever status the test sets. */
  public static class FlakyWebhook implements JavaDelegate {
    static final AtomicInteger STATUS = new AtomicInteger();
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void execute(DelegateExecution execution) {
      CALLS.incrementAndGet();
      int status = STATUS.get();
      WebhookFailures.record(execution, execution.getCurrentActivityId(), status, status >= 400 ? "HTTP " + status : null, null);
    }
  }

  private static final String BPMN = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:activiti="http://activiti.org/bpmn" targetNamespace="test">
        <process id="notify" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="callIrn"/>
          <serviceTask id="callIrn" activiti:async="true"
                       activiti:class="cv.igrp.platform.process.management.shared.delegates.webhook.WebhookAsyncRetryEngineTest$FlakyWebhook"/>
          <sequenceFlow id="f2" sourceRef="callIrn" targetRef="wait"/>
          <userTask id="wait"/>
          <sequenceFlow id="f3" sourceRef="wait" targetRef="end"/>
          <endEvent id="end"/>
        </process>
      </definitions>
      """;

  private ProcessEngine engine;
  private JdbcTemplate jdbc;
  private JobFailureRecorder failures;

  @BeforeEach
  void setUp() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:webhook-retry;DB_CLOSE_DELAY=1000");
    dataSource.setUser("sa"); // what the standalone engine connects as
    jdbc = new JdbcTemplate(dataSource);
    failures = new JobFailureRecorder(jdbc, new DataSourceTransactionManager(dataSource));
    var conf = new StandaloneInMemProcessEngineConfiguration();
    conf.setJdbcUrl("jdbc:h2:mem:webhook-retry;DB_CLOSE_DELAY=1000");
    conf.setTypedEventListeners(java.util.Map.<String, java.util.List<ActivitiEventListener>>of(
        ActivitiEventType.JOB_EXECUTION_FAILURE.name(), java.util.List.of(failures),
        ActivitiEventType.JOB_EXECUTION_SUCCESS.name(), java.util.List.of(failures)));
    conf.setAsyncExecutorActivate(false);
    // what AsyncJobRetryConfig sets from IGRP_ASYNC_JOB_*
    conf.setAsyncExecutorNumberOfRetries(2);
    conf.setAsyncFailedJobWaitTime(0);
    conf.setDefaultFailedJobWaitTime(0);
    engine = conf.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addString("notify.bpmn20.xml", BPMN).deploy();
    FlakyWebhook.CALLS.set(0);
    try {
      jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V15__create_job_failure.sql").readAllBytes()));
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterEach
  void tearDown() {
    jdbc.execute("DROP TABLE t_job_failure");
    engine.close();
  }

  @Test
  void exhaustedRetriesLandInTheDeadLetterAndAReprocessCompletesTheTask() {
    var management = engine.getManagementService();
    var runtime = engine.getRuntimeService();
    var failedJobs = new FailedJobService(management, runtime, mock(ProcessInstanceEntityRepository.class), failures, 1);
    FlakyWebhook.STATUS.set(503);

    var pi = runtime.startProcessInstanceByKey("notify");

    // attempt 1 (async job), attempt 2 (after the retry wait, as a timer job)
    runJob(management.createJobQuery().processInstanceId(pi.getId()).singleResult());
    runJob(management.createTimerJobQuery().processInstanceId(pi.getId()).singleResult());

    assertThat(FlakyWebhook.CALLS).hasValue(2);
    var page = failedJobs.list(null, 0, 20);
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().get(0).getActivityId()).isEqualTo("callIrn");
    assertThat(page.getContent().get(0).getExceptionMessage()).contains("HTTP 503");
    // recorded by us: Activiti keeps no timestamp and a new job id per attempt
    assertThat(page.getContent().get(0).getAttempts()).isEqualTo(2);
    assertThat(page.getContent().get(0).getLastFailedAt()).isNotNull();
    assertThat(page.getContent().get(0).getExecutionId()).isNotNull();
    assertThat(failedJobs.stacktrace(page.getContent().get(0).getJobId())).contains("WebhookCallException");

    FlakyWebhook.STATUS.set(200);
    failedJobs.retry(page.getContent().get(0).getJobId(), null, "ops@irn.mj.pt");
    management.executeJob(management.createJobQuery().processInstanceId(pi.getId()).singleResult().getId());

    assertThat(failedJobs.list(null, 0, 20).getTotalElements()).isZero();
    assertThat(engine.getTaskService().createTaskQuery().processInstanceId(pi.getId()).singleResult().getTaskDefinitionKey())
        .isEqualTo("wait");
    assertThat(runtime.getVariable(pi.getId(), "callIrnStatusCode")).isEqualTo(200);
    // the success clears the record, so a later failure on this path starts again at 1
    assertThat(jdbc.queryForObject("SELECT count(*) FROM t_job_failure", Integer.class)).isZero();
  }

  @Test
  void aDefinitive4xxDoesNotRetryAndIsRecordedForTheBpmn() {
    FlakyWebhook.STATUS.set(404);
    var pi = engine.getRuntimeService().startProcessInstanceByKey("notify");

    runJob(engine.getManagementService().createJobQuery().processInstanceId(pi.getId()).singleResult());

    assertThat(FlakyWebhook.CALLS).hasValue(1);
    assertThat(engine.getRuntimeService().getVariables(pi.getId()))
        .containsEntry("callIrnStatusCode", 404)
        .containsEntry("callIrnError", "HTTP 404");
  }

  private void runJob(Job job) {
    var management = engine.getManagementService();
    if (management.createTimerJobQuery().jobId(job.getId()).count() > 0) {
      // the move creates a new executable job with its own id
      management.moveTimerToExecutableJob(job.getId());
      job = management.createJobQuery().processInstanceId(job.getProcessInstanceId()).singleResult();
    }
    final String id = job.getId();
    if (FlakyWebhook.STATUS.get() >= 500) {
      assertThatThrownBy(() -> management.executeJob(id)).hasCauseInstanceOf(WebhookFailures.WebhookCallException.class);
    } else {
      management.executeJob(id);
    }
  }

}
