package cv.igrp.platform.process.management.shared.delegates.message.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.process.management.processruntime.domain.models.TaskAssignmentRuleRequest;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.delegates.message.dto.ProcessEventDTO;
import cv.igrp.platform.process.management.shared.delegates.message.dto.TaskAssignmentRuleDTO;
import cv.igrp.platform.process.management.shared.domain.models.Code;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractProcessEventConsumer {

  protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
  protected final ProcessInstanceService processInstanceService;
  protected final ObjectMapper objectMapper;
  protected final JwtDecoder jwtDecoder;

  protected AbstractProcessEventConsumer(ProcessInstanceService processInstanceService, ObjectMapper objectMapper, JwtDecoder jwtDecoder) {
    this.processInstanceService = processInstanceService;
    this.objectMapper = objectMapper;
    this.jwtDecoder = jwtDecoder;
  }

  protected void handleMessage(ConsumerRecord<String, String> record) {

    var message = record.value();

    LOGGER.info("Received process event: {}", message);

    ProcessEventDTO event = parseMessage(message);
    if (event == null || event.getBusinessKey() == null || event.getBusinessKey().isBlank()) {
      LOGGER.warn("Invalid or incomplete event message: {}", message);
      return;
    }

    Map<String, Object> vars = event.getVariables() != null ? event.getVariables() : Collections.emptyMap();
    List<TaskAssignmentRuleRequest> assignmentRules = toAssignmentRules(event.getAssignmentRules());

    try {

      String token = null;
      Header header = record.headers().lastHeader("Authorization");
      if (header != null) {
        token = new String(header.value(), StandardCharsets.UTF_8);
      }

      Authentication auth;

      if (token != null && token.startsWith("Bearer ")) {
        String jwt = token.substring(7);
        // validate/parse JWT → build JwtAuthenticationToken
        Jwt decoded = jwtDecoder.decode(jwt);
        auth = new JwtAuthenticationToken(decoded, extractAuthorities(decoded));
      } else {
        // Use a system account when no token is provided
        auth = systemAuthentication();
      }

      SecurityContextHolder.getContext().setAuthentication(auth);

      if (event.getMessageName() != null && !event.getMessageName().isBlank()) {
        LOGGER.info("Correlating message '{}' for businessKey '{}'", event.getMessageName(), event.getBusinessKey());
        processInstanceService.correlateMessage(event.getBusinessKey(), event.getMessageName(), vars, assignmentRules);
      } else {
        LOGGER.info("Signaling process instance for businessKey '{}'", event.getBusinessKey());
        processInstanceService.signal(event.getBusinessKey(), event.getTaskId(), vars, assignmentRules);
      }

      LOGGER.info("Processed event successfully for businessKey: {}", event.getBusinessKey());

    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private ProcessEventDTO parseMessage(String message) {
    try {
      return objectMapper.readValue(message, ProcessEventDTO.class);
    } catch (Exception e) {
      LOGGER.error("Failed to parse ProcessEventDTO: {}", e.getMessage());
      return null;
    }
  }

  private List<TaskAssignmentRuleRequest> toAssignmentRules(List<TaskAssignmentRuleDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .filter(dto -> dto.getTaskKey() != null && !dto.getTaskKey().isBlank())
        .map(dto -> TaskAssignmentRuleRequest.builder()
            .taskKey(Code.create(dto.getTaskKey().trim()))
            .assignee(dto.getAssignee() != null && !dto.getAssignee().isBlank()
                ? Code.create(dto.getAssignee().trim())
                : null)
            .candidateUsers(splitCommaSeparated(dto.getCandidateUsers()))
            .assignmentMode(dto.getAssignmentMode())
            .priority(dto.getPriority())
            .build())
        .toList();
  }

  private List<String> splitCommaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
  }

  protected List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
    // Extract authorities from JWT claims (adjust based on your JWT structure)
    Object roles = jwt.getClaim("roles");
    if (roles instanceof List<?>) {
      return ((List<?>) roles).stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()))
          .collect(Collectors.toList());
    }
    return List.of(new SimpleGrantedAuthority("ROLE_ACTIVITI_USER"), new SimpleGrantedAuthority("ROLE_ACTIVITI_ADMIN"));
  }

  protected Authentication systemAuthentication() {
    return new UsernamePasswordAuthenticationToken(
        "system-bot",
        null,
        List.of(new SimpleGrantedAuthority("ROLE_ACTIVITI_USER"), new SimpleGrantedAuthority("ROLE_ACTIVITI_ADMIN"))
    );
  }

}
