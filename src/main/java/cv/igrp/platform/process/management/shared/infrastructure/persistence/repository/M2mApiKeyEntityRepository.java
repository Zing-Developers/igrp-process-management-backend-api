package cv.igrp.platform.process.management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.M2mApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface M2mApiKeyEntityRepository extends JpaRepository<M2mApiKeyEntity, UUID> {

  Optional<M2mApiKeyEntity> findByKeyHash(String keyHash);

  /** Best-effort usage stamp — throttled by the caller, never on the auth decision path. */
  @Modifying
  @Query("update M2mApiKeyEntity k set k.lastUsedAt = :now where k.id = :id")
  void stampLastUsed(@Param("id") UUID id, @Param("now") Instant now);

}
