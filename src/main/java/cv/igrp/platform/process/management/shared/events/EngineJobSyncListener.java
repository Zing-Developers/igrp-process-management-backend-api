package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.config.AfterCommitExecutor;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After an engine job succeeds (async service task, timer, a reprocessed failed job) the process may
 * have reached a new user task or its end with no request around to copy that into the app. This
 * runs {@link ProcessInstanceService#syncFromEngine} once the job's transaction commits, in a new one.
 *
 * <p>Registered in the engine configuration (AsyncJobRetryConfig), so it is in place before the job
 * executor starts. The service is looked up lazily: holding it directly would close a bean cycle
 * (engine → listener → service → engine). Jobs that finish while the context is still starting are
 * queued and synced on ApplicationReadyEvent, so a job thread never asks Spring for a bean mid-startup.
 */
@Component
public class EngineJobSyncListener implements ActivitiEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(EngineJobSyncListener.class);

  private final ObjectProvider<ProcessInstanceService> processInstanceService;
  private final TransactionTemplate newTransaction;
  private final Set<String> pending = ConcurrentHashMap.newKeySet();
  private volatile boolean ready;

  private final AfterCommitExecutor afterCommit;

  public EngineJobSyncListener(ObjectProvider<ProcessInstanceService> processInstanceService,
                               PlatformTransactionManager transactionManager,
                               AfterCommitExecutor afterCommit) {
    this.processInstanceService = processInstanceService;
    this.afterCommit = afterCommit;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @EventListener(ApplicationReadyEvent.class)
  void onApplicationReady() {
    ready = true;
    drainPending();
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
          // the job's connection is only returned after this; the sync takes one of its own
          afterCommit.execute(() -> sync(processInstanceId));
        }
      });
    } else {
      sync(processInstanceId);
    }
  }

  void sync(String processInstanceId) {
    if (!ready) {
      pending.add(processInstanceId);
      // the app may have become ready between the check and the add
      if (ready) {
        drainPending();
      }
      return;
    }
    syncNow(processInstanceId);
  }

  private void drainPending() {
    for (String processInstanceId : pending) {
      if (pending.remove(processInstanceId)) {
        syncNow(processInstanceId);
      }
    }
  }

  private void syncNow(String processInstanceId) {
    try {
      newTransaction.executeWithoutResult(status -> processInstanceService.getObject().syncFromEngine(processInstanceId));
    } catch (RuntimeException e) {
      LOGGER.error("Could not sync process instance {} after a background job", processInstanceId, e);
    }
  }

  @Override
  public boolean isFailOnException() {
    return false;
  }

}
