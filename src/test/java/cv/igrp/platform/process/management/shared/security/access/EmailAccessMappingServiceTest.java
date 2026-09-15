package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.EmailAccessMappingEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import cv.igrp.platform.process.management.shared.security.AuditPrincipals;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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

class EmailAccessMappingServiceTest {

  private EmailAccessMappingEntityRepository repository;
  private EmailAccessMappingService service;

  @BeforeEach
  void setUp() {
    repository = mock(EmailAccessMappingEntityRepository.class);
    var profiles = mock(UserProfileRepository.class);
    when(profiles.findBySubjectOrEmail(any(), any())).thenReturn(Optional.empty());
    service = new EmailAccessMappingService(repository, new AuditPrincipals(profiles, new UserProfileMapper()), profiles);
  }

  @Test
  void createsAnActiveMappingWithTheEmailNormalised() {
    var created = service.create("  Svc-Fila@Parceiro.CV ", List.of(" TASK_INSTANCES:visualizar", "TASK_INSTANCES:visualizar"),
        " job da fila ", " pedido no ticket IRN-4521 ", null, "admin@nosi.cv");

    var captor = ArgumentCaptor.forClass(EmailAccessMappingEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getEmail()).isEqualTo("svc-fila@parceiro.cv");
    assertThat(saved.getPermissions()).isEqualTo("TASK_INSTANCES:visualizar");
    assertThat(saved.getDescription()).isEqualTo("job da fila");
    assertThat(saved.getNotes()).isEqualTo("pedido no ticket IRN-4521");
    assertThat(created.getNotes()).isEqualTo("pedido no ticket IRN-4521");
    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    assertThat(saved.getUpdatedBy()).isEqualTo("admin@nosi.cv");
    assertThat(created.getPermissions()).containsExactly("TASK_INSTANCES:visualizar");
    assertThat(created.getEmail()).isEqualTo("svc-fila@parceiro.cv");
  }

  @Test
  void rejectsRolesMalformedEmailsAndEmptyGrants() {
    // ROLE_DEPT_IGRP.superadmin as a "permission" would be a skeleton key
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("ROLE_DEPT_IGRP.superadmin"), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("roles are not allowed");
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("ROLE_X:y"), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.create("not-an-email", List.of("TASK_INSTANCES:visualizar"), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of(), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
  }

  @Test
  void secondActiveMappingForTheSameEmailIsA400NotA500() {
    // the service sees the live one first and names it
    var live = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    when(repository.findByEmailAndActiveTrue("svc@x.cv")).thenReturn(Optional.of(live));
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has an active mapping (" + live.getId());
    // the index still backs a race
    when(repository.findByEmailAndActiveTrue("svc@x.cv")).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup",
        new ConstraintViolationException("dup", null, "uq_email_access_mapping_active_email")));
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, null, null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has an active mapping");
  }

  @Test
  void otherIntegrityErrorsAreNotDisguisedAsDuplicates() {
    // a varchar(255) permissions column in a Hibernate-created schema surfaced as "already exists" once
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("value too long for type character varying(255)"));
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, null, null, "admin"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("value too long");
  }

  @Test
  void expiredMappingIsRetiredWhenTheEmailIsMappedAgain() {
    var expired = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    expired.setExpiresAt(Instant.now().minusSeconds(60));
    when(repository.findByEmailAndActiveTrue("svc@x.cv")).thenReturn(Optional.of(expired));

    var created = service.create("svc@x.cv", List.of("PROCESS_INSTANCES:visualizar"), null, null, null, "admin2");

    assertThat(expired.isActive()).isFalse();
    assertThat(expired.getRevokedBy()).isEqualTo("admin2");
    assertThat(created.getPermissions()).containsExactly("PROCESS_INSTANCES:visualizar");
    assertThat(created.isActive()).isTrue();
  }

  @Test
  void updateReplacesTheGrantAndRefusesRevokedMappings() {
    var entity = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

    var updated = service.update(entity.getId(), List.of("TASK_INSTANCES:editar"), "novo", "nota", null, "admin2");
    assertThat(entity.getNotes()).isEqualTo("nota");
    assertThat(entity.getPermissions()).isEqualTo("TASK_INSTANCES:editar");
    assertThat(entity.getUpdatedBy()).isEqualTo("admin2");
    assertThat(updated.getPermissions()).containsExactly("TASK_INSTANCES:editar");

    // the creator editing their own mapping: same principal twice in the audit lookup (e2e caught a Set.of here)
    assertThat(service.update(entity.getId(), List.of("TASK_INSTANCES:editar"), null, null, null, "admin").getUpdatedBy()).isEqualTo("admin");

    entity.setActive(false);
    assertThatThrownBy(() -> service.update(entity.getId(), List.of("TASK_INSTANCES:editar"), null, null, null, "admin2"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revoked");
  }

  @Test
  void revokeStampsWhoAndWhen() {
    var entity = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

    service.revoke(entity.getId(), "admin@nosi.cv");

    assertThat(entity.isActive()).isFalse();
    assertThat(entity.getRevokedBy()).isEqualTo("admin@nosi.cv");
    assertThat(entity.getRevokedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getRevokedAt());
    assertThat(entity.getUpdatedBy()).isEqualTo("admin@nosi.cv");
    assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), "admin")).isInstanceOf(IllegalArgumentException.class);
  }

  static EmailAccessMappingEntity active(String email, String permissions) {
    var e = new EmailAccessMappingEntity();
    e.setId(UUID.randomUUID());
    e.setEmail(email);
    e.setPermissions(permissions);
    e.setActive(true);
    e.setCreatedBy("admin");
    e.setCreatedAt(Instant.now());
    return e;
  }

  @Test
  void notesAreCappedAtTwoThousandCharacters() {
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, "x".repeat(2001), null, "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2000");
    service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, "x".repeat(2000), null, "admin");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listIsAPageNewestFirstWithBoundedSizeAndValidatedStatus() {
    var row = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    var captor = ArgumentCaptor.forClass(Pageable.class);
    when(repository.findAll(any(Specification.class), captor.capture())).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    var page = service.list(" SVC ", "active", null, 500);

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.isFirst()).isTrue();
    assertThat(captor.getValue().getPageSize()).isEqualTo(EmailAccessMappingService.PAGE_SIZE_MAX);
    assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection().isDescending()).isTrue();
    assertThatThrownBy(() -> service.list(null, "deleted", 0, 20)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status");
  }

  @Test
  void revokeIsIdempotentAndKeepsTheOriginalRevoker() {
    var entity = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
    service.revoke(entity.getId(), "admin-a");
    var firstAt = entity.getRevokedAt();
    service.revoke(entity.getId(), "admin-b");
    assertThat(entity.getRevokedBy()).isEqualTo("admin-a");
    assertThat(entity.getRevokedAt()).isEqualTo(firstAt);
    verify(repository, org.mockito.Mockito.times(1)).save(any());
  }

  @Test
  void validationFailuresHappenBeforeAnExpiredMappingIsRetired() {
    var expired = active("svc@x.cv", "TASK_INSTANCES:visualizar");
    expired.setExpiresAt(Instant.now().minusSeconds(60));
    when(repository.findByEmailAndActiveTrue("svc@x.cv")).thenReturn(Optional.of(expired));
    assertThatThrownBy(() -> service.create("svc@x.cv", List.of("TASK_INSTANCES:visualizar"), null, "x".repeat(2001), null, "admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(expired.isActive()).isTrue();
    verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
  }

  @Test
  void likePatternEscapesTheWildcards() {
    assertThat(EmailAccessMappingService.likePattern(" Svc_A%\\ ")).isEqualTo("%svc\\_a\\%\\\\%");
    assertThat(EmailAccessMappingService.likePattern("  ")).isNull();
  }

}
