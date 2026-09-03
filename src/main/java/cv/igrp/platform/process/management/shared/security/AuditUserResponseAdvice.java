package cv.igrp.platform.process.management.shared.security;

import cv.igrp.platform.process.management.area.application.dto.AreaDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.TaskInstanceDTO;
import cv.igrp.platform.process.management.processruntime.application.dto.UserProfileDTO;
import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills {@code userProfileCreatedBy}/{@code userProfileUpdatedBy} on every {@link AuditedResponse}
 * in a response body — top-level object, list, page, or nested (task → events, area → processes) —
 * with ONE batch profile lookup per response. Lives at the HTTP edge so no handler has to remember
 * to call it; the lifecycle profiles (startedBy, endedBy, performedBy…) keep their existing
 * model-level resolution and are untouched here.
 */
@RestControllerAdvice
public class AuditUserResponseAdvice implements ResponseBodyAdvice<Object> {

  private final UserProfileRepository userProfileRepository;
  private final UserProfileMapper userProfileMapper;

  public AuditUserResponseAdvice(UserProfileRepository userProfileRepository, UserProfileMapper userProfileMapper) {
    this.userProfileRepository = userProfileRepository;
    this.userProfileMapper = userProfileMapper;
  }

  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                ServerHttpRequest request, ServerHttpResponse response) {
    final var nodes = new ArrayList<AuditedResponse>();
    collect(body, nodes);
    if (nodes.isEmpty()) return body;

    final var principals = new HashSet<String>();
    for (var node : nodes) {
      addIfNotBlank(principals, node.getCreatedBy());
      addIfNotBlank(principals, node.getUpdatedBy());
    }
    if (principals.isEmpty()) return body;

    final var profiles = lookup(principals);
    for (var node : nodes) {
      node.setUserProfileCreatedBy(profiles.get(node.getCreatedBy()));
      node.setUserProfileUpdatedBy(profiles.get(node.getUpdatedBy()));
    }
    return body;
  }

  private static void collect(Object node, List<AuditedResponse> out) {
    switch (node) {
      case null -> { }
      case AuditedResponse audited -> {
        out.add(audited);
        for (var child : childrenOf(audited)) collect(child, out);
      }
      case AuditedPage page -> { if (page.getContent() != null) for (var row : page.getContent()) collect(row, out); }
      case Collection<?> items -> { for (var item : items) collect(item, out); }
      default -> { }
    }
  }

  private static Collection<? extends AuditedResponse> childrenOf(AuditedResponse node) {
    return switch (node) {
      case TaskInstanceDTO task when task.getTaskInstanceEvents() != null -> task.getTaskInstanceEvents();
      case AreaDTO area when area.getProcess() != null -> area.getProcess();
      default -> List.of();
    };
  }

  /** A stored principal may be a sub or an email (whatever the configured principal claim) — both are tried. */
  private Map<String, UserProfileDTO> lookup(Set<String> principals) {
    final var result = new HashMap<String, UserProfileDTO>();
    for (UserProfile profile : userProfileRepository.findBySubjectOrEmails(principals, principals)) {
      final var dto = userProfileMapper.toDTO(profile);
      if (profile.getSub() != null) result.put(profile.getSub(), dto);
      if (profile.getEmail() != null) result.put(profile.getEmail(), dto);
    }
    return result;
  }

  private static void addIfNotBlank(Set<String> set, String value) {
    if (value != null && !value.isBlank()) set.add(value);
  }

}
