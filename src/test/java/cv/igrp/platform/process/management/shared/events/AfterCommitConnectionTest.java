package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.config.AfterCommitExecutor;
import cv.igrp.platform.process.management.shared.config.AsyncJobRetryConfig;
import cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The work the job listeners do after a job commits (sync the app, clear the failure record) needs a
 * connection of its own. Done in afterCommit on the job thread, it ran while the job's connection was
 * still checked out: two per job thread. It is handed to AfterCommitExecutor instead, so the job
 * thread never holds more than its own. Here that executor only queues, to measure the job alone.
 */
class AfterCommitConnectionTest {

  /** Fails while FAIL is set, as a webhook that gets a 503. */
  public static class Flaky implements JavaDelegate {
    static final AtomicBoolean FAIL = new AtomicBoolean();

    @Override
    public void execute(DelegateExecution execution) {
      if (FAIL.get()) {
        throw new IllegalStateException("HTTP 503");
      }
    }
  }

  private static final String BPMN = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:activiti="http://activiti.org/bpmn" targetNamespace="test">
        <process id="notify" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="callIrn"/>
          <serviceTask id="callIrn" activiti:async="true"
                       activiti:class="cv.igrp.platform.process.management.shared.events.AfterCommitConnectionTest$Flaky"/>
          <sequenceFlow id="f2" sourceRef="callIrn" targetRef="wait"/>
          <userTask id="wait"/>
        </process>
      </definitions>
      """;

  private final AtomicInteger open = new AtomicInteger();
  private final AtomicInteger peak = new AtomicInteger();
  private final List<Runnable> queued = new ArrayList<>();
  private ProcessEngine engine;
  private JdbcTemplate jdbc;
  private ProcessInstanceService processInstances;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    var raw = new JdbcDataSource();
    raw.setURL("jdbc:h2:mem:after-commit;DB_CLOSE_DELAY=1000");
    raw.setUser("sa");
    var dataSource = new CountingDataSource(raw);
    var transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V15__create_job_failure.sql").readAllBytes()));
    jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V17__job_failure_reprocess.sql").readAllBytes()));

    processInstances = mock(ProcessInstanceService.class);
    // the real sync reads the app's and the engine's tables: a query of its own
    doAnswer(call -> jdbc.queryForObject("SELECT count(*) FROM t_job_failure", Integer.class))
        .when(processInstances).syncFromEngine(anyString());
    ObjectProvider<ProcessInstanceService> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(processInstances);
    var sync = new EngineJobSyncListener(provider, transactionManager, new AfterCommitExecutor(queued::add));
    sync.onApplicationReady();
    var failures = new JobFailureRecorder(jdbc, transactionManager, new AfterCommitExecutor(queued::add));

    var conf = new SpringProcessEngineConfiguration();
    conf.setDataSource(dataSource);
    conf.setTransactionManager(transactionManager);
    conf.setDatabaseSchemaUpdate("true");
    conf.setAsyncExecutorActivate(false);
    var config = new AsyncJobRetryConfig();
    config.asyncJobRetryConfigurer(3, java.time.Duration.ZERO).configure(conf);
    config.engineJobSyncConfigurer(sync, failures).configure(conf);
    engine = conf.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addString("notify.bpmn20.xml", BPMN).deploy();
  }

  @AfterEach
  void tearDown() {
    engine.close();
    jdbc.execute("DROP ALL OBJECTS");
  }

  @Test
  void aJobThatSucceedsAfterFailingHoldsOneConnectionAndItsCleanupRunsAfterwards() {
    var management = engine.getManagementService();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("notify").getId();
    Flaky.FAIL.set(true);
    var first = management.createJobQuery().processInstanceId(pi).singleResult().getId();
    try {
      management.executeJob(first);
    } catch (RuntimeException expected) {
      // recorded as attempt 1, retried as a timer
    }
    management.moveTimerToExecutableJob(management.createTimerJobQuery().processInstanceId(pi).singleResult().getId());
    assertThat(jdbc.queryForObject("SELECT attempts FROM t_job_failure", Integer.class)).isEqualTo(1);

    Flaky.FAIL.set(false);
    queued.clear();
    open.set(0);
    peak.set(0);
    management.executeJob(management.createJobQuery().processInstanceId(pi).singleResult().getId());

    // the job thread alone: no second connection for the sync or the cleanup
    assertThat(peak).hasValue(1);
    assertThat(queued).hasSize(2);

    queued.forEach(Runnable::run);
    verify(processInstances).syncFromEngine(pi);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM t_job_failure", Integer.class)).isZero();
  }

  @Test
  void theCleanupLeavesAFailureRecordedAfterTheCommit() {
    var management = engine.getManagementService();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("notify").getId();
    Flaky.FAIL.set(true);
    try {
      management.executeJob(management.createJobQuery().processInstanceId(pi).singleResult().getId());
    } catch (RuntimeException expected) {
      // attempt 1
    }
    management.moveTimerToExecutableJob(management.createTimerJobQuery().processInstanceId(pi).singleResult().getId());
    Flaky.FAIL.set(false);
    queued.clear();
    management.executeJob(management.createJobQuery().processInstanceId(pi).singleResult().getId());

    // before the queued cleanup runs, a next async task on the same execution fails
    jdbc.update("UPDATE t_job_failure SET last_failed_at = ?",
        java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(5)));
    queued.forEach(Runnable::run);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM t_job_failure", Integer.class)).isEqualTo(1);
  }

  /** Counts the connections checked out at once. */
  private class CountingDataSource extends DelegatingDataSource {
    CountingDataSource(javax.sql.DataSource target) {
      super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return counted(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return counted(super.getConnection(username, password));
    }

    private Connection counted(Connection connection) {
      peak.accumulateAndGet(open.incrementAndGet(), Math::max);
      final AtomicBoolean closed = new AtomicBoolean();
      return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
          (proxy, method, args) -> {
            if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
              open.decrementAndGet();
            }
            try {
              return method.invoke(connection, args);
            } catch (InvocationTargetException e) {
              throw e.getCause();
            }
          });
    }
  }
}
