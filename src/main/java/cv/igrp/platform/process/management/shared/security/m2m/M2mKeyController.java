package cv.igrp.platform.process.management.shared.security.m2m;

import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super-admin management of M2M API keys (docs/SPEC_M2M_AUTHORIZATION.md §6.2).
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

  // expiresAt comes in as zone-less LocalDateTime, the datetime shape of every other endpoint
  public record CreateRequest(String clientName, List<String> permissions, String email, LocalDateTime expiresAt) { }
  public record CreatedResponse(UUID id, String clientName, String key, String createdBy,
                                UserProfileDTO userProfileCreatedBy) { }

  @PostMapping
  public ResponseEntity<CreatedResponse> create(@RequestBody CreateRequest request, Authentication authentication) {
    final var expiresAt = request.expiresAt() == null
        ? null : request.expiresAt().atZone(ZoneId.systemDefault()).toInstant();
    final var created = service.create(request.clientName(), request.permissions(), request.email(),
        expiresAt, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreatedResponse(created.id(), created.clientName(), created.plaintextKey(),
            created.createdBy(), created.userProfileCreatedBy()));
  }

  @GetMapping
  public ResponseEntity<List<M2mKeyService.KeySummary>> list() {
    return ResponseEntity.ok(service.list());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication authentication) {
    service.revoke(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/rotate")
  public ResponseEntity<CreatedResponse> rotate(@PathVariable UUID id, Authentication authentication) {
    final var created = service.rotate(id, authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CreatedResponse(created.id(), created.clientName(), created.plaintextKey(),
            created.createdBy(), created.userProfileCreatedBy()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

}
