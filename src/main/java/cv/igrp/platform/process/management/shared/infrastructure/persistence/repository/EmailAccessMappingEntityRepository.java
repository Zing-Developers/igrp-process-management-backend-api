package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EmailAccessMappingEntityRepository
    extends JpaRepository<EmailAccessMappingEntity, UUID>, JpaSpecificationExecutor<EmailAccessMappingEntity> {

  /** At most one row thanks to the partial unique index. */
  Optional<EmailAccessMappingEntity> findByEmailAndActiveTrue(String email);

}
