package cv.igrp.platform.process.management.shared.security;

import cv.igrp.platform.process.management.processruntime.domain.models.UserProfile;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.shared.domain.models.Identifier;
import cv.igrp.platform.process.management.shared.domain.models.Name;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class IAMUserProfileSyncFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IAMUserProfileSyncFilter.class);

  private final UserProfileRepository userProfileRepository;

  public IAMUserProfileSyncFilter(UserProfileRepository userProfileRepository) {
    this.userProfileRepository = userProfileRepository;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String sub = jwt.getSubject();
      try {
        syncUserProfile(jwt);
      } catch (Exception e) {
        LOGGER.warn("Failed to sync IAM user profile. sub={}, error={}", sub, e.getMessage(), e);
      }
    }

    chain.doFilter(request, response);
  }

  private void syncUserProfile(Jwt jwt) {
    String sub = jwt.getSubject();

    if (!StringUtils.hasText(sub)) {
      LOGGER.debug("Skipping IAM user profile sync because JWT subject is missing");
      return;
    }

    String email = jwt.getClaimAsString("email");

    var existing = userProfileRepository.findBySubjectOrEmail(sub, email);
    if (existing.isEmpty()) {
      LOGGER.debug("Creating IAM user profile. sub={}", sub);
      userProfileRepository.save(buildProfile(jwt));
    } else {
      buildUpdatedProfile(existing.get(), jwt).ifPresent(updated -> {
        LOGGER.debug("Updating IAM user profile. sub={}", sub);
        userProfileRepository.save(updated);
      });
    }
  }

  private UserProfile buildProfile(Jwt jwt) {
    String sub = jwt.getSubject();
    String username = resolveUsername(jwt);

    if (!StringUtils.hasText(username)) {
      throw new IllegalStateException("Could not determine username from JWT claims. sub=" + sub);
    }

    String firstName = jwt.getClaimAsString("given_name");
    String lastName = jwt.getClaimAsString("family_name");

    return UserProfile.builder()
        .id(resolveIdentifier(sub))
        .sub(sub)
        .username(username)
        .email(jwt.getClaimAsString("email"))
        .firstName(StringUtils.hasText(firstName) ? Name.create(firstName) : null)
        .lastName(StringUtils.hasText(lastName) ? Name.create(lastName) : null)
        .build();
  }

  // Returns Optional.empty() if nothing changed, otherwise the updated profile
  private Optional<UserProfile> buildUpdatedProfile(UserProfile existing, Jwt jwt) {
    String sub = jwt.getSubject();
    String username = resolveUsername(jwt);
    String email = jwt.getClaimAsString("email");
    String firstName = jwt.getClaimAsString("given_name");
    String lastName = jwt.getClaimAsString("family_name");

    String existingFirst = existing.getFirstName() != null ? existing.getFirstName().getValue() : null;
    String existingLast = existing.getLastName() != null ? existing.getLastName().getValue() : null;

    boolean changed = (StringUtils.hasText(username) && !username.equals(existing.getUsername()))
        || (StringUtils.hasText(sub) && !sub.equals(existing.getSub()))
        || !Objects.equals(email, existing.getEmail())
        || !Objects.equals(firstName, existingFirst)
        || !Objects.equals(lastName, existingLast);

    if (!changed) return Optional.empty();

    return Optional.of(UserProfile.builder()
        .id(existing.getId())
        .sub(StringUtils.hasText(sub) ? sub : existing.getSub())
        .username(StringUtils.hasText(username) ? username : existing.getUsername())
        .email(email)
        .firstName(StringUtils.hasText(firstName) ? Name.create(firstName) : null)
        .lastName(StringUtils.hasText(lastName) ? Name.create(lastName) : null)
        .build());
  }

  private String resolveUsername(Jwt jwt) {
    String preferred = jwt.getClaimAsString("preferred_username");
    return StringUtils.hasText(preferred) ? preferred : jwt.getClaimAsString("email");
  }

  private Identifier resolveIdentifier(String sub) {
    try {
      return Identifier.create(UUID.fromString(sub));
    } catch (IllegalArgumentException ignored) {
      return Identifier.generate();
    }
  }

}
