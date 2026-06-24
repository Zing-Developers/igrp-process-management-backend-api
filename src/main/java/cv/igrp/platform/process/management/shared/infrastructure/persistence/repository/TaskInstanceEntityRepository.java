package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.application.constants.TaskInstanceStatus;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskInstanceEntityRepository extends
    JpaRepository<TaskInstanceEntity, UUID>,
    JpaSpecificationExecutor<TaskInstanceEntity>,
    RevisionRepository<TaskInstanceEntity, UUID, Integer>
{

  Optional<TaskInstanceEntity> findByExternalId(String processInstanceId);

  List<TaskInstanceEntity> findAllByExternalIdIn(Collection<String> externalIds);

  /**
   * Aggregates task counts grouped by status in a single query. Replaces the previous
   * per-status COUNT queries used by the global task statistics endpoint.
   */
  @Query("SELECT t.status AS status, COUNT(t) AS count FROM TaskInstanceEntity t GROUP BY t.status")
  List<TaskStatusCount> countGroupedByStatus();

  /** Projection for {@link #countGroupedByStatus()}. */
  interface TaskStatusCount {
    TaskInstanceStatus getStatus();
    long getCount();
  }

}
