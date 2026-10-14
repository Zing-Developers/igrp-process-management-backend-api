package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ProcessInstanceEntityRepository extends
    JpaRepository<ProcessInstanceEntity, UUID>,
    JpaSpecificationExecutor<ProcessInstanceEntity>,
    RevisionRepository<ProcessInstanceEntity, UUID, Integer>
{

  Optional<ProcessInstanceEntity> findByBusinessKey(String businessKey);

  Optional<ProcessInstanceEntity> findByEngineProcessNumber(String engineProcessNumber);

  /** Row lock held until the transaction ends: serializes the task syncs of one process. */
  @Query(value = "SELECT id FROM t_process_instance WHERE id = :id FOR UPDATE", nativeQuery = true)
  Optional<UUID> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

  List<ProcessInstanceEntity> findAllByProcReleaseId(String procReleaseId);

  List<ProcessInstanceEntity> findAllByEngineProcessNumberIn(Collection<String> engineProcessNumbers);

  /**
   * Aggregates process counts grouped by status in a single query. Replaces the previous
   * per-status COUNT queries used by the process statistics endpoint.
   */
  @Query("SELECT p.status AS status, COUNT(p) AS count FROM ProcessInstanceEntity p GROUP BY p.status")
  List<ProcessStatusCount> countGroupedByStatus();

  /** Projection for {@link #countGroupedByStatus()}. */
  interface ProcessStatusCount {
    ProcessInstanceStatus getStatus();
    long getCount();
  }

}
