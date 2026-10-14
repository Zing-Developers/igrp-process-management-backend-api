package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The reconciliation query against the real engine tables (H2) next to minimal copies of the app's
 * two tables: one process per kind of disagreement, plus one where app and engine agree.
 */
class TaskReconcilerEngineTest {

  static volatile boolean lockFree = true;

  /** Stands in for Postgres's pg_try_advisory_xact_lock (public: H2 calls it by reflection). */
  public static class AdvisoryLock {
    public static boolean tryLock(long key) {
      return lockFree;
    }
  }

  private static final String BPMN = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
        <process id="certidao" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="validarPedido"/>
          <userTask id="validarPedido"/>
          <sequenceFlow id="f2" sourceRef="validarPedido" targetRef="end"/>
          <endEvent id="end"/>
        </process>
      </definitions>
      """;

  private ProcessEngine engine;
  private JdbcTemplate jdbc;
  private DataSourceTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:reconcile;DB_CLOSE_DELAY=1000");
    transactionManager = new DataSourceTransactionManager(dataSource);
    var conf = new SpringProcessEngineConfiguration();
    conf.setDataSource(dataSource);
    conf.setTransactionManager(transactionManager);
    conf.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP);
    conf.setAsyncExecutorActivate(false);
    engine = conf.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addString("c.bpmn20.xml", BPMN).deploy();

    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE t_process_instance (id UUID PRIMARY KEY, engine_process_number VARCHAR(255), status VARCHAR(255))");
    jdbc.execute("CREATE TABLE t_task_instance (id UUID PRIMARY KEY, external_id VARCHAR(255), process_instance_id UUID,"
        + " status VARCHAR(255), started_at TIMESTAMP)");
    jdbc.execute("CREATE ALIAS pg_try_advisory_xact_lock FOR \"" + AdvisoryLock.class.getName() + ".tryLock\"");
    lockFree = true;
  }

  @AfterEach
  void tearDown() {
    jdbc.execute("DROP TABLE t_task_instance");
    jdbc.execute("DROP TABLE t_process_instance");
    jdbc.execute("DROP ALIAS pg_try_advisory_xact_lock");
    engine.close();
  }

  private String start() {
    return engine.getRuntimeService().startProcessInstanceByKey("certidao").getId();
  }

  private String engineTaskId(String processInstanceId) {
    return engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult().getId();
  }

  private UUID appProcess(String engineId, String status) {
    var id = UUID.randomUUID();
    jdbc.update("INSERT INTO t_process_instance VALUES (?, ?, ?)", id, engineId, status);
    return id;
  }

  private void appTask(UUID process, String externalId, String status) {
    jdbc.update("INSERT INTO t_task_instance VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), externalId, process, status,
        java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
  }

  @Test
  void findsEachKindOfDisagreementAndLeavesConsistentProcessesAlone() {
    // the engine has a task the app never got (a lost listener sync)
    var missingInApp = start();
    appProcess(missingInApp, "RUNNING");

    // the app still has a task open that the engine dropped (an interrupting timer)
    var goneFromEngine = start();
    var gone = appProcess(goneFromEngine, "RUNNING");
    appTask(gone, engineTaskId(goneFromEngine), "ASSIGNED");
    appTask(gone, "engine-task-gone", "ASSIGNED");

    // the engine completed the process; the app still has it running
    var ended = start();
    var endedApp = appProcess(ended, "RUNNING");
    appTask(endedApp, engineTaskId(ended), "COMPLETED");
    engine.getTaskService().complete(engineTaskId(ended));

    // app and engine agree
    var consistent = start();
    appTask(appProcess(consistent, "RUNNING"), engineTaskId(consistent), "CREATED");

    var reconciler = new TaskReconciler(jdbc, mock(ProcessInstanceService.class), transactionManager, Duration.ofMinutes(2));

    assertThat(reconciler.findDivergent(Instant.now().plusSeconds(60)))
        .containsExactlyInAnyOrder(missingInApp, goneFromEngine, ended);
    // inside the grace period nothing counts: requests and listener syncs may still be in flight
    assertThat(reconciler.findDivergent(Instant.now().minusSeconds(3600 * 2))).isEmpty();
  }

  @Test
  void syncsEveryDivergentProcessOnlyWhenItHoldsTheLock() {
    var missingInApp = start();
    appProcess(missingInApp, "RUNNING");
    var service = mock(ProcessInstanceService.class);
    var reconciler = new TaskReconciler(jdbc, service, transactionManager, Duration.ofSeconds(-60));

    lockFree = false;
    reconciler.reconcile();
    verify(service, never()).syncFromEngine(missingInApp);

    lockFree = true;
    reconciler.reconcile();
    verify(service).syncFromEngine(missingInApp);
  }

}
