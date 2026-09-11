package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailAccessMappingEntityRepository extends JpaRepository<EmailAccessMappingEntity, UUID> {

  /** At most one row thanks to the partial unique index in V10. */
  Optional<EmailAccessMappingEntity> findByEmailAndActiveTrue(String email);

}
