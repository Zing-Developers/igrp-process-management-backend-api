package cv.igrp.platform.process.management.shared.security.util;

import cv.igrp.platform.process.management.shared.domain.models.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityUserContext implements UserContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUserContext.class);

  @Override
  public Code getCurrentUser() {
    return Code.create(getCurrentUserName());
  }

  /**
   * Resolves the current user name from the authentication principal.
   * The principal is set to the email claim by JwtAuthenticationConverter.
   * If email is missing from the JWT, falls back to: preferred_username → sub.
   */
  @Override
  public String getCurrentUserName() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    // Primary: principal name (set to email by JwtAuthenticationConverter)
    String name = authentication.getName();
    if (name != null && !name.isBlank()) {
      return name;
    }

    // Fallback: extract from JWT claims when email is missing
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();

      String preferredUsername = jwt.getClaimAsString("preferred_username");
      if (preferredUsername != null && !preferredUsername.isBlank()) {
        LOGGER.warn("JWT missing 'email' claim, falling back to 'preferred_username' [{}]", preferredUsername);
        return preferredUsername;
      }

      String sub = jwt.getSubject();
      LOGGER.warn("JWT missing 'email' and 'preferred_username' claims, falling back to 'sub' [{}]", sub);
      return sub;
    }

    return name;
  }

  @Override
  public List<String> getCurrentGroups() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return List.of();
    }

    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .filter(role -> role.startsWith(ActivitiConstants.GROUP_PREFIX))
        .map(role -> role.substring(ActivitiConstants.GROUP_PREFIX.length()))
        .toList();
  }

  @Override
  public List<String> getCurrentRoles() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return List.of();
    }

    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .filter(role -> role.startsWith(IgrpAuthorizationConstants.ROLE_PREFIX))
        .map(role -> role.substring(IgrpAuthorizationConstants.ROLE_PREFIX.length()))
        .toList();
  }

  @Override
  public boolean isSuperAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    return authentication.getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(r -> r.contains(IgrpAuthorizationConstants.SUPER_ADMIN_ROLE));
  }

}
