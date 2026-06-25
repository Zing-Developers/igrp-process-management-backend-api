package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.application.constants.ProcessInstanceStatus;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.ProcessInstanceEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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

  List<ProcessInstanceEntity> findAllByProcReleaseId(String procReleaseId);

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
