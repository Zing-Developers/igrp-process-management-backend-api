package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.TaskAssignmentRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskAssignmentRuleEntityRepository extends
    JpaRepository<TaskAssignmentRuleEntity, UUID>,
    JpaSpecificationExecutor<TaskAssignmentRuleEntity> {
}
