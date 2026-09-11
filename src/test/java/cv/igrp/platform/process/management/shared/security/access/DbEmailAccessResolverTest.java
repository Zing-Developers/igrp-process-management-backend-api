package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.platform.process.management.shared.infrastructure.persistence.entity.EmailAccessMappingEntity;
import cv.igrp.platform.process.management.shared.infrastructure.persistence.repository.EmailAccessMappingEntityRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbEmailAccessResolverTest {

  private final EmailAccessMappingEntityRepository repository = mock(EmailAccessMappingEntityRepository.class);
  private final DbEmailAccessResolver resolver = new DbEmailAccessResolver(repository);

  @Test
  void activeMappingYieldsItsPermissionsLookedUpLowerCased() {
    var mapping = EmailAccessMappingServiceTest.active("svc@x.cv", " TASK_INSTANCES:visualizar, PROCESS_INSTANCES:criar ,");
    when(repository.findByEmailAndActiveTrue("svc@x.cv")).thenReturn(Optional.of(mapping));

    assertThat(resolver.resolve(" Svc@X.cv ")).containsExactly("TASK_INSTANCES:visualizar", "PROCESS_INSTANCES:criar");
    verify(repository).findByEmailAndActiveTrue("svc@x.cv");
  }

  @Test
  void unknownOrExpiredMappingsGrantNothing() {
    when(repository.findByEmailAndActiveTrue(any())).thenReturn(Optional.empty());
    assertThat(resolver.resolve("nobody@x.cv")).isEmpty();

    var expired = EmailAccessMappingServiceTest.active("old@x.cv", "TASK_INSTANCES:visualizar");
    expired.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
    when(repository.findByEmailAndActiveTrue("old@x.cv")).thenReturn(Optional.of(expired));
    assertThat(resolver.resolve("old@x.cv")).isEmpty();

    var future = EmailAccessMappingServiceTest.active("new@x.cv", "TASK_INSTANCES:visualizar");
    future.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
    when(repository.findByEmailAndActiveTrue("new@x.cv")).thenReturn(Optional.of(future));
    assertThat(resolver.resolve("new@x.cv")).containsExactly("TASK_INSTANCES:visualizar");
  }

}
