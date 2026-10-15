package cv.igrp.platform.process.management.shared.jobs;

import cv.igrp.platform.process.management.shared.application.dto.FailedJobListPageDTO;
import cv.igrp.platform.process.management.shared.application.dto.FailedJobRetryRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Reprocessing console for async jobs that ran out of retries (docs/DELEGATES.md, "Resiliência").
 * Route permissions: FAILED_JOBS:visualizar to list, :diagnosticar for the stack trace, :reprocessar to retry.
 */
@Tag(name = "FailedJobs", description = "Jobs assíncronos (ex.: webhooks com activiti:async) que esgotaram as tentativas. "
    + "Listar, ver o erro e voltar a pôr na fila.")
@RestController
@RequestMapping("/failed-jobs")
public class FailedJobController {

  private final FailedJobService service;

  public FailedJobController(FailedJobService service) {
    this.service = service;
  }

  @Operation(
    summary = "Listar jobs falhados (paginado)",
    description = "Dead-letter jobs, os mais antigos primeiro, com a instância da app, o activityId e a mensagem de erro. "
        + "Exige FAILED_JOBS:visualizar.",
    responses = {
      @ApiResponse(responseCode = "200", description = "Página de jobs falhados",
          content = @Content(mediaType = "application/json", schema = @Schema(implementation = FailedJobListPageDTO.class, type = "object"))),
      @ApiResponse(responseCode = "400", description = "processInstanceId inexistente", content = @Content)
    }
  )
  @GetMapping
  public ResponseEntity<FailedJobListPageDTO> list(
      @Parameter(description = "Id (UUID) da instância de processo na app; omitido = todas") @RequestParam(value = "processInstanceId", required = false) UUID processInstanceId,
      @Parameter(description = "Página, a começar em 0") @RequestParam(value = "page", required = false) Integer page,
      @Parameter(description = "Tamanho da página, default 20, máximo 100") @RequestParam(value = "size", required = false) Integer size) {
    return ResponseEntity.ok(service.list(processInstanceId, page, size));
  }

  @Operation(
    summary = "Stack trace de um job falhado",
    description = "Stack trace completa da última falha. Pode conter detalhes internos: exige FAILED_JOBS:diagnosticar.",
    responses = {
      @ApiResponse(responseCode = "200", description = "Stack trace", content = @Content(mediaType = "text/plain")),
      @ApiResponse(responseCode = "400", description = "Job inexistente ou já não está falhado", content = @Content)
    }
  )
  @GetMapping(value = "/{jobId}/stacktrace", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> stacktrace(@Parameter(description = "Id do job") @PathVariable String jobId) {
    return ResponseEntity.ok(service.stacktrace(jobId));
  }

  @Operation(
    summary = "Reprocessar um job falhado",
    description = "Devolve o job à fila com N tentativas (body opcional; default IGRP_ASYNC_JOB_RETRIES); corre na próxima "
        + "aquisição do job executor. Se voltar a falhar todas as vezes, regressa aos jobs falhados. Exige FAILED_JOBS:reprocessar.",
    responses = {
      @ApiResponse(responseCode = "204", description = "Job de volta à fila", content = @Content),
      @ApiResponse(responseCode = "400", description = "Job inexistente ou retries fora de 1..100", content = @Content)
    }
  )
  @PostMapping("/{jobId}/retry")
  public ResponseEntity<Void> retry(@Parameter(description = "Id do job") @PathVariable String jobId,
                                    @RequestBody(required = false) FailedJobRetryRequestDTO request,
                                    Authentication authentication) {
    service.retry(jobId, request != null ? request.getRetries() : null, authentication.getName());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(Map.of("error", e.getMessage()));
  }

}
