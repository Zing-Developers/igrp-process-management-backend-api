package cv.igrp.platform.process.management.shared.config;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * A job due at boot ran before the context had finished wiring Activiti and failed. The executor now
 * waits for the application to be ready, and a job created before that runs once it is.
 */
class JobExecutorStartConfigTest {

  public static class Counting implements JavaDelegate {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void execute(DelegateExecution execution) {
      CALLS.incrementAndGet();
    }
  }

  private static final String BPMN = """
      <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:activiti="http://activiti.org/bpmn" targetNamespace="test">
        <process id="notify" isExecutable="true">
          <startEvent id="start"/>
          <sequenceFlow id="f1" sourceRef="start" targetRef="call"/>
          <serviceTask id="call" activiti:async="true"
                       activiti:class="cv.igrp.platform.process.management.shared.config.JobExecutorStartConfigTest$Counting"/>
          <sequenceFlow id="f2" sourceRef="call" targetRef="end"/>
          <endEvent id="end"/>
        </process>
      </definitions>
      """;

  private final JobExecutorStartConfig config = new JobExecutorStartConfig();
  private ProcessEngine engine;

  @BeforeEach
  void setUp() {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:job-executor-start-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    var conf = new SpringProcessEngineConfiguration();
    conf.setDataSource(dataSource);
    conf.setTransactionManager(new DataSourceTransactionManager(dataSource));
    conf.setDatabaseSchemaUpdate("true");
    // what Boot copies from spring.activiti.async-executor-activate (true by default), before the configurers
    conf.setAsyncExecutorActivate(true);
    conf.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(50);
    config.deferJobExecutorConfigurer().configure(conf);
    engine = conf.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addString("notify.bpmn20.xml", BPMN).deploy();
    Counting.CALLS.set(0);
  }

  @AfterEach
  void tearDown() {
    engine.close();
  }

  @Test
  void aJobCreatedDuringStartupWaitsForTheApplicationAndThenRuns() throws Exception {
    assertThat(asyncExecutorActive()).isFalse();
    engine.getRuntimeService().startProcessInstanceByKey("notify");
    Thread.sleep(300);
    assertThat(Counting.CALLS).hasValue(0);
    assertThat(engine.getManagementService().createJobQuery().count()).isEqualTo(1);

    config.jobExecutorStarter(provider(engine), true).onApplicationEvent(mock(ApplicationReadyEvent.class));

    assertThat(asyncExecutorActive()).isTrue();
    waitUntil(() -> engine.getManagementService().createJobQuery().count() == 0);
    assertThat(Counting.CALLS).hasValue(1);
  }

  @Test
  void staysOffWhenTheExecutorIsTurnedOff() {
    config.jobExecutorStarter(provider(engine), false).onApplicationEvent(mock(ApplicationReadyEvent.class));

    assertThat(asyncExecutorActive()).isFalse();
  }

  @Test
  void aSecondReadyEventDoesNotRestartIt() {
    var starter = config.jobExecutorStarter(provider(engine), true);
    starter.onApplicationEvent(mock(ApplicationReadyEvent.class));
    starter.onApplicationEvent(mock(ApplicationReadyEvent.class));

    assertThat(asyncExecutorActive()).isTrue();
  }

  private boolean asyncExecutorActive() {
    return engine.getProcessEngineConfiguration().getAsyncExecutor().isActive();
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<ProcessEngine> provider(ProcessEngine engine) {
    var provider = (ObjectProvider<ProcessEngine>) mock(ObjectProvider.class);
    doAnswer(call -> {
      ((Consumer<ProcessEngine>) call.getArgument(0)).accept(engine);
      return null;
    }).when(provider).ifAvailable(any());
    return provider;
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
    var deadline = System.currentTimeMillis() + 10_000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }
}
