package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingDTO;
import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super-admin management of email access mappings (docs/SPEC_EMAIL_ACCESS_MAPPING.md). Payload DTOs
 * live in shared/application/dto and are modelled in .igrpstudio/shared.
 *
 * <p>The route gate in SecurityConfig requires a JWT super-admin — a mapped token or an M2M key can
 * never reach these endpoints, whatever permissions they carry. Nothing secret is exchanged here.
 */
@RestController
@RequestMapping("/email-access-mappings")
public class EmailAccessMappingController {

  private final EmailAccessMappingService service;

  public EmailAccessMappingController(EmailAccessMappingService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<EmailAccessMappingDTO> create(@RequestBody EmailAccessMappingRequestDTO request,
                                                      Authentication authentication) {
    final var created = service.create(request.getEmail(), request.getPermissions(), request.getDescription(),
        request.getNotes(), toInstant(request.getExpiresAt()), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  public ResponseEntity<List<EmailAccessMappingDTO>> list() {
    return ResponseEntity.ok(service.list());
  }

  @PutMapping("/{id}")
  public ResponseEntity<EmailAccessMappingDTO> update(@PathVariable UUID id,
                                                      @RequestBody EmailAccessMappingRequestDTO request,
                                                      Authentication authentication) {
    return ResponseEntity.ok(service.update(id, request.getPermissions(), request.getDescription(),
        request.getNotes(), toInstant(request.getExpiresAt()), authentication.getName()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication authentication) {
    service.revoke(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  /** expiresAt comes in as zone-less LocalDateTime, the datetime shape of every other endpoint. */
  private static Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

}
