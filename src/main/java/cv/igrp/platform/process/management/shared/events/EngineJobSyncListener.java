package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import jakarta.annotation.PostConstruct;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * After an engine job succeeds (async service task, timer, a reprocessed failed job) the process may
 * have reached a new user task or its end with no request around to copy that into the app. This
 * runs {@link ProcessInstanceService#syncFromEngine} once the job's transaction commits, in a new one.
 *
 * <p>Registered on the RuntimeService after startup rather than as an engine-config listener, which
 * would close a bean cycle (engine → listener → service → engine).
 */
@Component
public class EngineJobSyncListener implements ActivitiEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(EngineJobSyncListener.class);

  private final RuntimeService runtimeService;
  private final ProcessInstanceService processInstanceService;
  private final TransactionTemplate newTransaction;

  public EngineJobSyncListener(RuntimeService runtimeService, ProcessInstanceService processInstanceService,
                               PlatformTransactionManager transactionManager) {
    this.runtimeService = runtimeService;
    this.processInstanceService = processInstanceService;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // ponytail: a job that succeeds while the context is still starting (before this runs) is not
  // synced; the next request or job on that process catches up, since the sync skips known tasks
  @PostConstruct
  void register() {
    runtimeService.addEventListener(this, ActivitiEventType.JOB_EXECUTION_SUCCESS);
  }

  @Override
  public void onEvent(ActivitiEvent event) {
    final String processInstanceId = event.getProcessInstanceId();
    if (processInstanceId == null) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // the engine's rows are not visible to another transaction until the job commits
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sync(processInstanceId);
        }
      });
    } else {
      sync(processInstanceId);
    }
  }

  void sync(String processInstanceId) {
    try {
      newTransaction.executeWithoutResult(status -> processInstanceService.syncFromEngine(processInstanceId));
    } catch (RuntimeException e) {
      LOGGER.error("Could not sync process instance {} after a background job", processInstanceId, e);
    }
  }

  @Override
  public boolean isFailOnException() {
    return false;
  }

}
