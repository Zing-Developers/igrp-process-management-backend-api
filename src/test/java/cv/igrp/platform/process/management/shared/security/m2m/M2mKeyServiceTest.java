package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.M2mApiKeyEntity;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.M2mApiKeyEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class M2mKeyServiceTest {

  private M2mApiKeyEntityRepository repository;
  private M2mKeyService service;

  @BeforeEach
  void setUp() {
    repository = mock(M2mApiKeyEntityRepository.class);
    service = new M2mKeyService(repository, new M2mKeyCodec("test-pepper"),
        mock(UserProfileRepository.class), new UserProfileMapper(), Duration.ofDays(7));
  }

  @Test
  void createsAKeyWithPrefixAndStoresOnlyTheHash() {

    var created = service.create("fila-job", List.of("TASK_INSTANCES:visualizar"), null, null, "admin");

    assertThat(created.plaintextKey()).startsWith("igrpm2m_");

    var captor = ArgumentCaptor.forClass(M2mApiKeyEntity.class);
    verify(repository).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getKeyHash()).isNotEqualTo(created.plaintextKey());
    assertThat(saved.getKeyPrefix()).startsWith("igrpm2m_");
    assertThat(saved.isActive()).isTrue();
  }

  @Test
  void rejectsRoleStringsInPermissions() {
    // ROLE_DEPT_IGRP.superadmin as a "permission" would be a skeleton key (M-11)
    assertThatThrownBy(() ->
        service.create("evil", List.of("ROLE_DEPT_IGRP.superadmin"), null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
        service.create("evil", List.of("task_instances:View"), null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonSlugClientNames() {
    assertThatThrownBy(() ->
        service.create("Fila Job!", List.of("TASK_INSTANCES:visualizar"), null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rotateStampsAForcedExpiryOnTheOldKey() {

    var old = new M2mApiKeyEntity();
    old.setId(UUID.randomUUID());
    old.setClientName("fila-job");
    old.setPermissions("TASK_INSTANCES:visualizar");
    when(repository.findById(old.getId())).thenReturn(Optional.of(old));

    var replacement = service.rotate(old.getId(), "admin");

    assertThat(replacement.plaintextKey()).startsWith("igrpm2m_");
    // the overlap cannot live forever: old key gets now + grace (M-18)
    assertThat(old.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    assertThat(old.getExpiresAt()).isBefore(Instant.now().plus(Duration.ofDays(8)));
  }

}
