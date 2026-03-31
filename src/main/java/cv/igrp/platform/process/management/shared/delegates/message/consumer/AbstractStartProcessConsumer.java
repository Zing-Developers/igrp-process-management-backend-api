package cv.igrp.platform.process.management.shared.delegates.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.process.management.processruntime.domain.models.ProcessInstance;
import cv.igrp.platform.process.management.processruntime.domain.service.ProcessInstanceService;
import cv.igrp.platform.process.management.shared.delegates.message.dto.ProcessVariableDTO;
import cv.igrp.platform.process.management.shared.delegates.message.dto.StartProcessDTO;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractStartProcessConsumer {

  protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
  protected final ProcessInstanceService processInstanceService;
  protected final ObjectMapper objectMapper;
  protected final JwtDecoder jwtDecoder;

  protected AbstractStartProcessConsumer(ProcessInstanceService processInstanceService,
                                         ObjectMapper objectMapper, JwtDecoder jwtDecoder) {
    this.processInstanceService = processInstanceService;
    this.objectMapper = objectMapper;
    this.jwtDecoder = jwtDecoder;
  }

  protected void handleMessage(ConsumerRecord<String, String> record) {

    var message = record.value();

    LOGGER.info("Received message: {}", message);

    StartProcessDTO dto = parseMessage(message);
    if (dto == null) {
      LOGGER.warn("Ignored invalid message: {}", message);
      return;
    }

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

      processInstanceService.createAndStartProcessInstance(toModel(dto), "system-bot@nosi.cv");
      LOGGER.info("Started process instance for processDefinitionId: {}", dto.getProcessDefinitionId());
      LOGGER.debug("Full DTO content: {}", dto);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private StartProcessDTO parseMessage(String message) {
    try {
      return objectMapper.readValue(message, StartProcessDTO.class);
    } catch (Exception e) {
      LOGGER.error("Failed to parse message into StartProcessDTO: {}", e.getMessage());
      return null;
    }
  }

  private ProcessInstance toModel(StartProcessDTO dto) {
    Map<String, Object> vars = dto.getVariables().stream()
        .filter(v -> v.getName() != null)
        .collect(Collectors.toMap(
            ProcessVariableDTO::getName,
            ProcessVariableDTO::getValue,
            (a, b) -> b
        ));
    return ProcessInstance.builder()
        .procReleaseId(dto.getProcessDefinitionId() != null ? Code.create(dto.getProcessDefinitionId()) : null)
        .procReleaseKey(Code.create(dto.getProcessKey()))
        .businessKey(dto.getBusinessKey() != null ? Code.create(dto.getBusinessKey()) : null)
        .applicationBase(Code.create(dto.getApplicationBase()))
        .variables(vars)
        .priority(dto.getPriority())
        .build();
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
