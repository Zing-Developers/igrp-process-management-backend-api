package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.IAMUserProfileEntity;
import cv.igrp.platform.process.management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface IAMUserProfileEntityRepository extends
    JpaRepository<IAMUserProfileEntity, UUID>,
    JpaSpecificationExecutor<IAMUserProfileEntity>,
    RevisionRepository<IAMUserProfileEntity, UUID, Integer> {

  default IAMUserProfileEntity findByIdOrThrow(UUID id) {
    return this.findById(id)
        .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "IAMUserProfileEntity not found for id: " + id));
  }

  Optional<IAMUserProfileEntity> findByUsername(String username);

  Optional<IAMUserProfileEntity> findByEmail(String email);

  Optional<IAMUserProfileEntity> findBySub(String id);

  List<IAMUserProfileEntity> findBySubIn(Collection<String> ids);

  List<IAMUserProfileEntity> findByEmailIn(Collection<String> emails);

}
