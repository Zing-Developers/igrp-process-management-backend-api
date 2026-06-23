package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

}
