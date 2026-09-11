package cv.igrp.platform.process.management.shared.security.access;

import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingDTO;
import cv.igrp.platform.process.management.shared.application.dto.EmailAccessMappingRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "EmailAccessMapping", description = "Mapeamentos email → permissões para sistemas externos com token Keycloak sem sessão IRN "
    + "(docs/SPEC_EMAIL_ACCESS_MAPPING.md). Exige JWT com EMAIL_ACCESS_MAPPINGS:<acao> e cookie de sessão IRN, ou super-admin; "
    + "um token mapeado ou uma chave M2M recebem 403.")
@RestController
@RequestMapping("/email-access-mappings")
public class EmailAccessMappingController {

  private final EmailAccessMappingService service;

  public EmailAccessMappingController(EmailAccessMappingService service) {
    this.service = service;
  }

  @Operation(
    summary = "Criar mapeamento email → permissões",
    description = "Concede ao portador de um token Keycloak cujo claim email bate (guardado em minúsculas) as permissões "
        + "MODULO:acao indicadas, para pedidos sem sessão IRN. Uma linha activa por email. Exige EMAIL_ACCESS_MAPPINGS:criar.",
    responses = {
      @ApiResponse(responseCode = "201", description = "Mapeamento activo",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAccessMappingDTO.class, type = "object"))),
      @ApiResponse(responseCode = "400", description = "Email inválido, lista de permissões vazia, permissão fora de MODULO:acao (ROLE_*/GROUP_* rejeitados) ou email já com mapeamento activo",
          content = @Content(mediaType = "application/json",
              schema = @Schema(type = "object"), examples = @ExampleObject(value = "{\"error\": \"invalid permission 'ROLE_DEPT_IGRP.superadmin': expected MODULE:action (roles are not allowed)\"}"))),
      @ApiResponse(responseCode = "403", description = "Sem a permissão, sem sessão IRN, token mapeado ou chave M2M", content = @Content)
    }
  )
  @PostMapping
  public ResponseEntity<EmailAccessMappingDTO> create(@RequestBody EmailAccessMappingRequestDTO request,
                                                      Authentication authentication) {
    final var created = service.create(request.getEmail(), request.getPermissions(), request.getDescription(),
        request.getNotes(), toInstant(request.getExpiresAt()), authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @Operation(
    summary = "Listar mapeamentos",
    description = "Todos os mapeamentos, revogados incluídos, com o trio de auditoria (criado, alterado, revogado por). "
        + "Estado: active=false → revogado; expiresAt no passado → expirado. Exige EMAIL_ACCESS_MAPPINGS:visualizar.",
    responses = {
      @ApiResponse(responseCode = "200", description = "Lista de mapeamentos",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAccessMappingDTO.class, type = "array"))),
      @ApiResponse(responseCode = "403", description = "Sem a permissão, sem sessão IRN, token mapeado ou chave M2M", content = @Content)
    }
  )
  @GetMapping
  public ResponseEntity<List<EmailAccessMappingDTO>> list() {
    return ResponseEntity.ok(service.list());
  }

  @Operation(
    summary = "Editar mapeamento",
    description = "Substitui permissões, descrição, notas e expiração por inteiro. O email nunca muda: para outro endereço "
        + "cria-se outro mapeamento. Só mapeamentos activos. Exige EMAIL_ACCESS_MAPPINGS:editar.",
    responses = {
      @ApiResponse(responseCode = "200", description = "Mapeamento actualizado",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailAccessMappingDTO.class, type = "object"))),
      @ApiResponse(responseCode = "400", description = "Mapeamento revogado ou inexistente, lista vazia ou permissão inválida",
          content = @Content(mediaType = "application/json",
              schema = @Schema(type = "object"), examples = @ExampleObject(value = "{\"error\": \"invalid permission 'ROLE_DEPT_IGRP.superadmin': expected MODULE:action (roles are not allowed)\"}"))),
      @ApiResponse(responseCode = "403", description = "Sem a permissão, sem sessão IRN, token mapeado ou chave M2M", content = @Content)
    }
  )
  @PutMapping("/{id}")
  public ResponseEntity<EmailAccessMappingDTO> update(@Parameter(description = "Id do mapeamento") @PathVariable UUID id,
                                                      @RequestBody EmailAccessMappingRequestDTO request,
                                                      Authentication authentication) {
    return ResponseEntity.ok(service.update(id, request.getPermissions(), request.getDescription(),
        request.getNotes(), toInstant(request.getExpiresAt()), authentication.getName()));
  }

  @Operation(
    summary = "Revogar mapeamento",
    description = "Revogação soft (active=false, revokedBy/At), efectiva no pedido seguinte do sistema externo; sem undo. "
        + "A linha fica para auditoria e o email pode voltar a ser mapeado. Exige EMAIL_ACCESS_MAPPINGS:eliminar.",
    responses = {
      @ApiResponse(responseCode = "204", description = "Revogado", content = @Content),
      @ApiResponse(responseCode = "400", description = "Id inexistente", content = @Content(mediaType = "application/json",
              schema = @Schema(type = "object"), examples = @ExampleObject(value = "{\"error\": \"invalid permission 'ROLE_DEPT_IGRP.superadmin': expected MODULE:action (roles are not allowed)\"}"))),
      @ApiResponse(responseCode = "403", description = "Sem a permissão, sem sessão IRN, token mapeado ou chave M2M", content = @Content)
    }
  )
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@Parameter(description = "Id do mapeamento") @PathVariable UUID id, Authentication authentication) {
    service.revoke(id, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  /** expiresAt comes in as zone-less LocalDateTime, the datetime shape of every other endpoint. */
  private static Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    // always JSON: with an XML converter on the classpath a request without Accept would get <Map>
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("error", e.getMessage()));
  }

}
