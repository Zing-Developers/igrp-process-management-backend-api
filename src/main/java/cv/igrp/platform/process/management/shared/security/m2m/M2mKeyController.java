package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.platform.process.management.shared.application.dto.M2mKeyCreatedDTO;
import cv.igrp.platform.process.management.shared.application.dto.M2mKeyRequestDTO;
import cv.igrp.platform.process.management.shared.application.dto.M2mKeySummaryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super-admin management of M2M API keys (docs/SPEC_M2M_AUTHORIZATION.md §6.2). Payload DTOs live in
 * shared/application/dto and are modelled in .igrpstudio/shared (see .igrpstudio/README.md).
 *
 * <p>The route gate in SecurityConfig requires a JWT super-admin — an M2M key can never reach these
 * endpoints, whatever permissions it carries (M-12). The created key's plaintext appears once in the
 * creation/rotation response and nowhere else; request/response bodies of these routes must never be
 * logged.
 */
@RestController
@RequestMapping("/m2m-keys")
public class M2mKeyController {

  private final M2mKeyService service;

  public M2mKeyController(M2mKeyService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<M2mKeyCreatedDTO> create(@RequestBody M2mKeyRequestDTO request, Authentication authentication) {
    // expiresAt comes in as zone-less LocalDateTime, the datetime shape of every other endpoint
    final var expiresAt = request.getExpiresAt() == null
        ? null : request.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
    final var created = service.create(request.getClientName(), request.getPermissions(), request.getEmail(),
        expiresAt, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(toCreated(created));
  }

  @GetMapping
  public ResponseEntity<List<M2mKeySummaryDTO>> list() {
    return ResponseEntity.ok(service.list());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication authentication) {
    service.revoke(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/rotate")
  public ResponseEntity<M2mKeyCreatedDTO> rotate(@PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED).body(toCreated(service.rotate(id, authentication.getName())));
  }

  private static M2mKeyCreatedDTO toCreated(M2mKeyService.CreatedKey created) {
    return new M2mKeyCreatedDTO(created.id(), created.clientName(), created.plaintextKey(),
        created.createdBy(), created.userProfileCreatedBy());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

}
