package cv.igrp.platform.process.management.shared.events;

import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for {@link EngineJobSyncListener}: its sync runs in memory after a job commits, so a pod
 * that dies in between, or a sync that throws, leaves the app behind the engine. Every interval this
 * finds the processes where the two disagree and runs the same sync on them:
 * <ul>
 *   <li>an active engine task the app does not have;</li>
 *   <li>a task open in the app that is no longer active in the engine;</li>
 *   <li>a process running in the app that the engine has completed (not deleted).</li>
 * </ul>
 * Only rows older than the grace period count, so a request or a listener sync still in flight is
 * left alone. One pod at a time: a Postgres advisory lock held for the run's transaction.
 */
@Component
@EnableScheduling
public class TaskReconciler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReconciler.class);

  /** Arbitrary, fixed: identifies this job's advisory lock. */
  static final long LOCK_KEY = 0x1629_7A5C_0B0EL;
  static final int BATCH = 500;

  static final String DIVERGENT = """
      SELECT t.PROC_INST_ID_ FROM ACT_RU_TASK t
        JOIN t_process_instance p ON p.engine_process_number = t.PROC_INST_ID_ AND p.status <> 'COMPLETED'
        LEFT JOIN t_task_instance a ON a.external_id = t.ID_
       WHERE a.id IS NULL AND t.SUSPENSION_STATE_ = 1 AND t.CREATE_TIME_ < ?
      UNION
      SELECT p.engine_process_number FROM t_task_instance a
        JOIN t_process_instance p ON p.id = a.process_instance_id
        JOIN ACT_RU_EXECUTION e ON e.ID_ = p.engine_process_number AND e.SUSPENSION_STATE_ = 1
        LEFT JOIN ACT_RU_TASK t ON t.ID_ = a.external_id
       WHERE a.status IN ('CREATED', 'ASSIGNED') AND t.ID_ IS NULL AND a.started_at < ?
      UNION
      SELECT p.engine_process_number FROM t_process_instance p
        JOIN ACT_HI_PROCINST h ON h.PROC_INST_ID_ = p.engine_process_number
       WHERE p.status = 'RUNNING' AND h.END_TIME_ IS NOT NULL AND h.DELETE_REASON_ IS NULL AND h.END_TIME_ < ?
      """;

  private final JdbcTemplate jdbc;
  private final ProcessInstanceService processInstanceService;
  private final TransactionTemplate runTransaction;
  private final TransactionTemplate perProcess;
  private final Duration grace;

  public TaskReconciler(JdbcTemplate jdbc, ProcessInstanceService processInstanceService,
                        PlatformTransactionManager transactionManager,
                        @Value("${igrp.task-reconcile.grace:2m}") Duration grace) {
    this.jdbc = jdbc;
    this.processInstanceService = processInstanceService;
    this.runTransaction = new TransactionTemplate(transactionManager);
    this.perProcess = new TransactionTemplate(transactionManager);
    this.perProcess.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.grace = grace;
  }

  @Scheduled(initialDelayString = "${igrp.task-reconcile.interval:5m}", fixedDelayString = "${igrp.task-reconcile.interval:5m}")
  public void reconcile() {
    final Integer synced = runTransaction.execute(status -> {
      if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY))) {
        return 0; // another pod is on it
      }
      int done = 0;
      for (String processInstanceId : findDivergent(Instant.now().minus(grace))) {
        try {
          // its own transaction: one bad process does not undo the others
          perProcess.executeWithoutResult(s -> processInstanceService.syncFromEngine(processInstanceId));
          done++;
        } catch (RuntimeException e) {
          LOGGER.error("Reconcile: could not sync process instance {}", processInstanceId, e);
        }
      }
      return done;
    });
    if (synced != null && synced > 0) {
      LOGGER.warn("Reconcile: synced {} process instance(s) the app had fallen behind on", synced);
    }
  }

  List<String> findDivergent(Instant cutoff) {
    final Timestamp before = Timestamp.from(cutoff);
    return jdbc.queryForList(DIVERGENT + " LIMIT " + BATCH, String.class, before, before, before);
  }

}
