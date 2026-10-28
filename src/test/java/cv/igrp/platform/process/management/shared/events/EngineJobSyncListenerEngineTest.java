package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.config.AsyncJobRetryConfig;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.task.Task;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The app copies engine tasks only inside a request (start/signal/complete pull the active tasks).
 * An async service task moves on later, on the job executor: before the listener the next user task
 * reached the engine and never the app. Spring-managed engine on H2, so "after commit" is real.
 */
class EngineJobSyncListenerEngineTest {

  public static class OkWebhook implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
    }
  }

  private static String bpmn(boolean async) {
    return """
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:activiti="http://activiti.org/bpmn" targetNamespace="test">
          <process id="certidao" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="validarPedido"/>
            <userTask id="validarPedido"/>
            <sequenceFlow id="f2" sourceRef="validarPedido" targetRef="notifyIrn"/>
            <serviceTask id="notifyIrn" activiti:async="%s"
                         activiti:class="cv.igrp.platform.process.management.shared.events.EngineJobSyncListenerEngineTest$OkWebhook"/>
            <sequenceFlow id="f3" sourceRef="notifyIrn" targetRef="emitirCertidao"/>
            <userTask id="emitirCertidao"/>
            <sequenceFlow id="f4" sourceRef="emitirCertidao" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """.formatted(async);
  }

  private ProcessEngine engine;
  private EngineJobSyncListener listener;
  /** What the app's sync would find in the engine, captured at the moment it runs. */
  private final List<List<String>> seenBySync = new ArrayList<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:job-sync;DB_CLOSE_DELAY=1000");
    var transactionManager = new DataSourceTransactionManager(dataSource);

    var processInstances = mock(ProcessInstanceService.class);
    doAnswer(call -> {
      // a connection of its own sees only what the job committed (an engine query here would
      // reuse the job's open command context and see its uncommitted rows)
      seenBySync.add(committedTaskKeys(dataSource, call.getArgument(0)));
      return null;
    }).when(processInstances).syncFromEngine(anyString());
    ObjectProvider<ProcessInstanceService> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(processInstances);
    listener = new EngineJobSyncListener(provider, transactionManager, new cv.igrp.platform.process.management.shared.config.AfterCommitExecutor(Runnable::run));

    var conf = new SpringProcessEngineConfiguration();
    conf.setDataSource(dataSource);
    conf.setTransactionManager(transactionManager);
    conf.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP);
    conf.setAsyncExecutorActivate(false);
    // what the app does: registered in the configuration, before the engine (and its job executor) starts
    new AsyncJobRetryConfig().engineJobSyncConfigurer(listener, mock(cv.igrp.platform.process.management.shared.jobs.JobFailureRecorder.class)).configure(conf);
    engine = conf.buildProcessEngine();
  }

  @AfterEach
  void tearDown() {
    engine.close();
  }

  private static List<String> committedTaskKeys(JdbcDataSource dataSource, String processInstanceId) throws Exception {
    try (var connection = dataSource.getConnection();
         var query = connection.prepareStatement("SELECT TASK_DEF_KEY_ FROM ACT_RU_TASK WHERE PROC_INST_ID_ = ?")) {
      query.setString(1, processInstanceId);
      var keys = new ArrayList<String>();
      try (var rows = query.executeQuery()) {
        while (rows.next()) {
          keys.add(rows.getString(1));
        }
      }
      return keys;
    }
  }

  private List<String> activeTaskKeys(String processInstanceId) {
    return engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).active().list().stream()
        .map(Task::getTaskDefinitionKey).toList();
  }

  /** What completeTask does in one request: complete in the engine, then pull the active tasks. */
  private List<String> completeAndPull(String processInstanceId, String taskKey) {
    var tasks = engine.getTaskService();
    tasks.complete(tasks.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(taskKey).singleResult().getId());
    return activeTaskKeys(processInstanceId);
  }

  @Test
  void synchronousTaskIsPulledByTheRequestAndNoJobRuns() {
    listener.onApplicationReady();
    engine.getRepositoryService().createDeployment().addString("c.bpmn20.xml", bpmn(false)).deploy();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("certidao");

    assertThat(completeAndPull(pi.getId(), "validarPedido")).containsExactly("emitirCertidao");
    assertThat(seenBySync).isEmpty();
  }

  @Test
  void asyncTaskIsSyncedAfterTheJobCommitsAndSeesTheNextUserTask() {
    listener.onApplicationReady();
    engine.getRepositoryService().createDeployment().addString("c.bpmn20.xml", bpmn(true)).deploy();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("certidao");

    // the request's own pull finds nothing: the webhook has not run yet
    assertThat(completeAndPull(pi.getId(), "validarPedido")).isEmpty();
    assertThat(seenBySync).isEmpty();

    var management = engine.getManagementService();
    management.executeJob(management.createJobQuery().processInstanceId(pi.getId()).singleResult().getId());

    // once per job, after its commit, with the new user task already visible
    assertThat(seenBySync).containsExactly(List.of("emitirCertidao"));
  }

  /** A user task, then a wait on a timer, then the next user task. */
  private static final String TIMER_CATCH = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
        <process id="prazo" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="validarPedido"/>
          <userTask id="validarPedido"/>
          <sequenceFlow id="f2" sourceRef="validarPedido" targetRef="aguardarPrazo"/>
          <intermediateCatchEvent id="aguardarPrazo">
            <timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition>
          </intermediateCatchEvent>
          <sequenceFlow id="f3" sourceRef="aguardarPrazo" targetRef="emitirCertidao"/>
          <userTask id="emitirCertidao"/>
          <sequenceFlow id="f4" sourceRef="emitirCertidao" targetRef="end"/>
          <endEvent id="end"/>
        </process>
      </definitions>
      """;

  /** An interrupting deadline on a user task: when it fires the task is cancelled and escalated. */
  private static final String TIMER_BOUNDARY = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
        <process id="sla" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="validarPedido"/>
          <userTask id="validarPedido"/>
          <boundaryEvent id="slaExpirado" attachedToRef="validarPedido" cancelActivity="true">
            <timerEventDefinition><timeDuration>P2D</timeDuration></timerEventDefinition>
          </boundaryEvent>
          <sequenceFlow id="f2" sourceRef="slaExpirado" targetRef="escalarPedido"/>
          <userTask id="escalarPedido"/>
          <sequenceFlow id="f3" sourceRef="escalarPedido" targetRef="end"/>
          <sequenceFlow id="f4" sourceRef="validarPedido" targetRef="end"/>
          <endEvent id="end"/>
        </process>
      </definitions>
      """;

  /** What the async executor does when a timer is due: move it to the executable queue and run it. */
  private void fireTimer(String processInstanceId) {
    var management = engine.getManagementService();
    var timer = management.createTimerJobQuery().processInstanceId(processInstanceId).singleResult();
    management.moveTimerToExecutableJob(timer.getId());
    management.executeJob(management.createJobQuery().processInstanceId(processInstanceId).singleResult().getId());
  }

  @Test
  void timerCatchEventIsSyncedWhenItFires() {
    listener.onApplicationReady();
    engine.getRepositoryService().createDeployment().addString("prazo.bpmn20.xml", TIMER_CATCH).deploy();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("prazo");

    // the request ends on the timer: nothing to pull yet
    assertThat(completeAndPull(pi.getId(), "validarPedido")).isEmpty();

    fireTimer(pi.getId());

    assertThat(seenBySync).containsExactly(List.of("emitirCertidao"));
  }

  @Test
  void boundaryTimerIsSyncedWithTheEscalationTask() {
    listener.onApplicationReady();
    engine.getRepositoryService().createDeployment().addString("sla.bpmn20.xml", TIMER_BOUNDARY).deploy();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("sla");
    assertThat(activeTaskKeys(pi.getId())).containsExactly("validarPedido");

    fireTimer(pi.getId());

    // the engine cancelled validarPedido and opened escalarPedido; the sync sees only the new one
    assertThat(seenBySync).containsExactly(List.of("escalarPedido"));
  }


  @Test
  void aJobThatFinishesWhileTheAppIsStartingIsSyncedOnceTheAppIsReady() {
    engine.getRepositoryService().createDeployment().addString("prazo.bpmn20.xml", TIMER_CATCH).deploy();
    var pi = engine.getRuntimeService().startProcessInstanceByKey("prazo");
    completeAndPull(pi.getId(), "validarPedido");

    // e.g. a timer already due when the pod restarted, run before the context is up
    fireTimer(pi.getId());
    assertThat(seenBySync).isEmpty();

    listener.onApplicationReady();
    assertThat(seenBySync).containsExactly(List.of("emitirCertidao"));

    // drained, not replayed
    listener.onApplicationReady();
    assertThat(seenBySync).hasSize(1);
  }

}
