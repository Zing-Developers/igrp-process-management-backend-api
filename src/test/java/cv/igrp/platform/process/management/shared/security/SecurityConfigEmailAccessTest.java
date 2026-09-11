package cv.igrp.platform.process.management.shared.security;

import cv.igrp.framework.process.runtime.auth.core.access.EmailAccessResolver;
import cv.igrp.framework.process.runtime.auth.irn.adapter.IrnAuthorizationCacheService;
import cv.igrp.framework.process.runtime.auth.irn.adapter.IrnAuthorizationServiceAdapter;
import cv.igrp.framework.process.runtime.auth.irn.adapter.integration.config.IrnApiProperties;
import cv.igrp.framework.process.runtime.auth.core.adapter.IAuthorizationServiceAdapter;
import cv.igrp.framework.process.runtime.auth.core.adapter.IRouteAuthorizationAdapter;
import cv.igrp.framework.process.runtime.auth.core.adapter.RouteAuthorizationRule;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKey;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKeyResolver;
import cv.igrp.platform.process.management.processruntime.domain.repository.UserProfileRepository;
import cv.igrp.platform.process.management.processruntime.mappers.UserProfileMapper;
import cv.igrp.platform.process.management.shared.security.access.EmailAccessMappingController;
import cv.igrp.platform.process.management.shared.security.access.EmailAccessMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The filter chain end to end with the real converter, the real IRN adapter (session first) and the
 * real framework format gate; only IRN and the stores are mocked. What a session-less token can and cannot do
 * (docs/SPEC_EMAIL_ACCESS_MAPPING.md).
 */
@WebMvcTest(controllers = EmailAccessMappingController.class)
@Import({SecurityConfig.class, IAMUserProfileSyncFilter.class, SecurityConfigEmailAccessTest.Fixture.class,
    SecurityConfigEmailAccessTest.Probe.class})
@TestPropertySource(properties = {
    "igrp.security.principal-claim-name=email",
    "igrp.authorization.service.adapter=default",
    "AUTH_JWT_ISSUER=http://issuer.test"})
class SecurityConfigEmailAccessTest {

  private static final String SUPER_ADMIN = "admin@nosi.cv";

  @TestConfiguration
  static class Fixture {
    /** The real IRN adapter, session first; only IRN itself (/Auth/me) is mocked. */
    @Bean IAuthorizationServiceAdapter authorizationService(IrnAuthorizationCacheService irn, EmailAccessResolver resolver) {
      return new IrnAuthorizationServiceAdapter(irn, new IrnApiProperties("https://irn.test", SUPER_ADMIN, "session_id"), resolver);
    }

    /** Two catalogued routes, deny-unmatched: read at chain build time, so a real bean, not a mock. */
    @Bean IRouteAuthorizationAdapter routes() {
      return new IRouteAuthorizationAdapter() {
        @Override public List<RouteAuthorizationRule> getRules() {
          return List.of(
              new RouteAuthorizationRule(HttpMethod.POST, "/tasks-instances/search", Set.of("TASK_INSTANCES:visualizar")),
              new RouteAuthorizationRule(HttpMethod.POST, "/tasks-instances/*/claim", Set.of("TASK_INSTANCES:editar")),
              // the console itself, catalogued like the rest (one method per tier, like the framework derives)
              new RouteAuthorizationRule(HttpMethod.GET, "/email-access-mappings", Set.of("EMAIL_ACCESS_MAPPINGS:visualizar")),
              new RouteAuthorizationRule(HttpMethod.POST, "/email-access-mappings", Set.of("EMAIL_ACCESS_MAPPINGS:criar")),
              new RouteAuthorizationRule(HttpMethod.DELETE, "/email-access-mappings/*", Set.of("EMAIL_ACCESS_MAPPINGS:eliminar")));
        }
        @Override public boolean denyUnmatched() { return true; }
      };
    }
  }

  /** Handlers behind the two catalogued routes. */
  @RestController
  static class Probe {
    @PostMapping("/tasks-instances/search") String search() { return "ok"; }
    @PostMapping("/tasks-instances/{id}/claim") String claim() { return "ok"; }
  }

  @Autowired MockMvc mvc;
  @MockitoBean EmailAccessResolver resolver;
  @MockitoBean IrnAuthorizationCacheService irn;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean M2mKeyResolver m2mKeys;
  @MockitoBean EmailAccessMappingService service;
  @MockitoBean UserProfileRepository profiles;
  // pulled in by the app's controller advices, which @WebMvcTest scans
  @MockitoBean UserProfileMapper userProfileMapper;
  // @EnableJpaAuditing on the application class needs a metamodel; none in a web slice
  @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

  @BeforeEach
  void catalogue() {
    when(profiles.findBySubjectOrEmail(any(), any())).thenReturn(Optional.empty());
    token("mapped", "svc@parceiro.cv");
    token("no-email", null);
    token("admin", SUPER_ADMIN);
    token("manager", "gestor@nosi.cv");
  }

  private void token(String value, String email) {
    var jwt = Jwt.withTokenValue(value).header("alg", "RS256").subject("sub-" + value);
    if (email != null) jwt.claim("email", email);
    when(decoder.decode(value)).thenReturn(jwt.build());
  }

  @Test
  void mappedTokenPassesTheMappedRouteOnly() throws Exception {
    when(resolver.resolve("svc@parceiro.cv")).thenReturn(Set.of("TASK_INSTANCES:visualizar"));
    mvc.perform(post("/tasks-instances/search").header("Authorization", "Bearer mapped")).andExpect(status().isOk());
    mvc.perform(post("/tasks-instances/1/claim").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
  }

  @Test
  void roleWrittenIntoTheColumnNeverReachesManagementRoutes() throws Exception {
    when(resolver.resolve("svc@parceiro.cv")).thenReturn(Set.of("ROLE_DEPT_IGRP.superadmin", "TASK_INSTANCES:visualizar"));
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
    mvc.perform(get("/m2m-keys").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
    mvc.perform(post("/tasks-instances/1/claim").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
  }

  @Test
  void tokenWithoutEmailIsDeniedWithoutALookup() throws Exception {
    mvc.perform(post("/tasks-instances/search").header("Authorization", "Bearer no-email")).andExpect(status().isForbidden());
    verify(resolver, never()).resolve(any());
  }

  @Test
  void storeFailureFailsClosedEvenForTheSuperAdmin() throws Exception {
    when(resolver.resolve(any())).thenThrow(new IllegalStateException("db down"));
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
    mvc.perform(post("/tasks-instances/search").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
  }

  @Test
  void superAdminWithoutASessionManagesMappings() throws Exception {
    when(resolver.resolve(any())).thenReturn(Set.of());
    when(service.list()).thenReturn(List.of());
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer admin")).andExpect(status().isOk());
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
  }

  @Test
  void m2mKeyCannotReachManagementRoutesWhateverItCarries() throws Exception {
    when(m2mKeys.resolve("igrpm2m_test")).thenReturn(Optional.of(new M2mKey("job", Set.of("TASK_INSTANCES:visualizar"))));
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer igrpm2m_test")).andExpect(status().isForbidden());
    mvc.perform(post("/tasks-instances/search").header("Authorization", "Bearer igrpm2m_test")).andExpect(status().isOk());
  }

  // --- the console: catalogue permission, honoured only with an IRN session ---

  @Test
  void managerWithIrnSessionAndPermissionUsesTheConsole() throws Exception {
    when(irn.getPermissions("s1")).thenReturn(Set.of("EMAIL_ACCESS_MAPPINGS:visualizar"));
    when(service.list()).thenReturn(List.of());
    var session = new Cookie("session_id", "s1");
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer manager").cookie(session)).andExpect(status().isOk());
    // no :criar / :eliminar in the profile
    mvc.perform(post("/email-access-mappings").header("Authorization", "Bearer manager").cookie(session)
        .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    mvc.perform(delete("/email-access-mappings/1").header("Authorization", "Bearer manager").cookie(session)).andExpect(status().isForbidden());
    verify(resolver, never()).resolve(any());
  }

  @Test
  void consolePermissionFromTheMappingNeverCounts() throws Exception {
    // the store hands out the console permissions to a session-less token: structurally useless
    when(resolver.resolve("svc@parceiro.cv")).thenReturn(Set.of("EMAIL_ACCESS_MAPPINGS:visualizar", "EMAIL_ACCESS_MAPPINGS:criar"));
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer mapped")).andExpect(status().isForbidden());
    // a bogus cookie switches the adapter to the session path, where IRN knows nothing about it
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer mapped").cookie(new Cookie("session_id", "bogus")))
        .andExpect(status().isForbidden());
    verify(irn).getPermissions("bogus");
  }

  @Test
  void sessionWithoutThePermissionIsDenied() throws Exception {
    when(irn.getPermissions("s2")).thenReturn(Set.of("TASK_INSTANCES:visualizar"));
    mvc.perform(get("/email-access-mappings").header("Authorization", "Bearer manager").cookie(new Cookie("session_id", "s2")))
        .andExpect(status().isForbidden());
  }

}
