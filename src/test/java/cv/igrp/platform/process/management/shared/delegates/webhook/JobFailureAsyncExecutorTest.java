package cv.igrp.platform.process.management.shared.delegates.webhook;

import cv.igrp.platform.process.management.shared.delegates.webhook.WebhookAsyncRetryEngineTest.FlakyWebhook;
import cv.igrp.platform.process.management.shared.jobs.FailedJobService;
import cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.ProcessInstanceEntityRepository;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * As in production: a Spring-managed engine with the async executor running the jobs. That executor
 * fires JOB_EXECUTION_SUCCESS before the task runs and JOB_EXECUTION_FAILURE right after, so the
 * failure record must only be cleared once the job's transaction commits. When it was cleared on the
 * event itself, every failure started over at attempts=1 and a reprocess was never counted.
 */
class JobFailureAsyncExecutorTest {

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
  private FailedJobService failedJobs;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:job-failure-async;DB_CLOSE_DELAY=1000");
    dataSource.setUser("sa");
    var transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V15__create_job_failure.sql").readAllBytes()));
    jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V17__job_failure_reprocess.sql").readAllBytes()));
    failures = new JobFailureRecorder(jdbc, transactionManager);

    var conf = new SpringProcessEngineConfiguration();
    conf.setDataSource(dataSource);
    conf.setTransactionManager(transactionManager);
    conf.setDatabaseSchemaUpdate("true");
    conf.setTypedEventListeners(Map.<String, List<ActivitiEventListener>>of(
        ActivitiEventType.JOB_EXECUTION_FAILURE.name(), List.of(failures),
        ActivitiEventType.JOB_EXECUTION_SUCCESS.name(), List.of(failures)));
    conf.setAsyncExecutorActivate(true);
    conf.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(50);
    conf.setAsyncExecutorDefaultTimerJobAcquireWaitTime(50);
    // what AsyncJobRetryConfig sets from IGRP_ASYNC_JOB_*
    conf.setAsyncExecutorNumberOfRetries(2);
    conf.setAsyncFailedJobWaitTime(0);
    conf.setDefaultFailedJobWaitTime(0);
    engine = conf.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addString("notify.bpmn20.xml", BPMN).deploy();
    failedJobs = new FailedJobService(engine.getManagementService(), engine.getRuntimeService(), engine.getRepositoryService(),
        mock(ProcessInstanceEntityRepository.class), failures, 1);
    FlakyWebhook.CALLS.set(0);
  }

  @AfterEach
  void tearDown() {
    engine.close();
    jdbc.execute("DROP ALL OBJECTS");
  }

  @Test
  void attemptsAndReprocessesAddUpAcrossRunsAndASuccessClearsThem() throws Exception {
    var management = engine.getManagementService();
    FlakyWebhook.STATUS.set(503);
    var pi = engine.getRuntimeService().startProcessInstanceByKey("notify").getId();

    awaitUntil(() -> management.createDeadLetterJobQuery().processInstanceId(pi).count() == 1);
    var failed = failedJobs.list(null, null, 0, 20).getContent().get(0);
    assertThat(FlakyWebhook.CALLS).hasValue(2);
    assertThat(failed.getAttempts()).isEqualTo(2);
    assertThat(failed.getReprocessCount()).isZero();

    // one more attempt, run by the executor, still failing
    failedJobs.retry(failed.getJobId(), null, "ops@irn.mj.pt");
    awaitUntil(() -> FlakyWebhook.CALLS.get() == 3 && management.createDeadLetterJobQuery().processInstanceId(pi).count() == 1);
    var again = failedJobs.list(null, null, 0, 20).getContent().get(0);
    assertThat(again.getAttempts()).isEqualTo(3);
    assertThat(again.getReprocessCount()).isEqualTo(1);
    assertThat(again.getLastReprocessedBy()).isEqualTo("ops@irn.mj.pt");
    assertThat(again.getFirstFailedAt()).isEqualTo(failed.getFirstFailedAt());
    assertThat(failedJobs.stuckActivities(pi)).singleElement()
        .satisfies(activity -> assertThat(activity.getFailure().reprocessCount()).isEqualTo(1));

    FlakyWebhook.STATUS.set(200);
    failedJobs.retry(again.getJobId(), null, "ops@irn.mj.pt");
    awaitUntil(() -> engine.getTaskService().createTaskQuery().processInstanceId(pi).count() == 1);
    awaitUntil(() -> jdbc.queryForObject("SELECT count(*) FROM t_job_failure", Integer.class) == 0);
    assertThat(failedJobs.list(null, null, 0, 20).getTotalElements()).isZero();
    assertThat(failedJobs.stuckActivities(pi)).isEmpty();
  }

  private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
    final long deadline = System.currentTimeMillis() + 15_000;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("condition not met within 15s");
      }
      Thread.sleep(50);
    }
  }
}
