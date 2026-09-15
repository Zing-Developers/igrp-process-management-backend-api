package cv.igrp.platform.process.management.shared.security;

import cv.igrp.framework.process.runtime.auth.core.adapter.IAuthorizationServiceAdapter;
import cv.igrp.framework.process.runtime.auth.core.adapter.IRouteAuthorizationAdapter;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mAuthenticationManagers;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mKeyResolver;
import cv.igrp.framework.process.runtime.auth.core.m2m.M2mOpaqueTokenIntrospector;
import cv.igrp.platform.process.management.shared.security.util.ActivitiConstants;
import cv.igrp.platform.process.management.shared.security.util.IgrpAuthorizationConstants;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static cv.igrp.platform.process.management.shared.security.util.IgrpAuthorizationConstants.ROLE_PREFIX;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

  private static final String EMAIL_ACCESS_ROUTE = "/email-access-mappings";


  private final IAuthorizationServiceAdapter authorizationService;

  private final IRouteAuthorizationAdapter routeAuthorization;

  private final String principalClaimName;

  private final String corsAllowedOrigins;

  public SecurityConfig(IAuthorizationServiceAdapter authorizationService,
                        IRouteAuthorizationAdapter routeAuthorization,
                        @Value("${igrp.security.principal-claim-name}") String principalClaimName,
                        @Value("${igrp.cors.allowed-origins:}") String corsAllowedOrigins) {
    this.authorizationService = authorizationService;
    this.routeAuthorization = routeAuthorization;
    this.principalClaimName = principalClaimName;
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 IAMUserProfileSyncFilter iamUserProfileSyncFilter,
                                                 JwtDecoder jwtDecoder,
                                                 M2mKeyResolver m2mKeyResolver) throws Exception {

    http.cors(cors -> cors.configurationSource(request -> {
      // No configured origins = no CORS headers at all: cross-origin browser calls are refused.
      // Wildcard origins with credentials (the previous setup) let any site ride the user's
      // auth context (SECURITY_RECOMMENDATIONS P0).
      if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
        return null;
      }
      var configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
          .map(String::trim).filter(o -> !o.isEmpty()).toList());
      configuration.addAllowedMethod(HttpMethod.GET);
      configuration.addAllowedMethod(HttpMethod.POST);
      configuration.addAllowedMethod(HttpMethod.PUT);
      configuration.addAllowedMethod(HttpMethod.PATCH);
      configuration.addAllowedMethod(HttpMethod.DELETE);
      configuration.addAllowedMethod(HttpMethod.HEAD);
      configuration.addAllowedMethod(HttpMethod.OPTIONS);
      configuration.addAllowedHeader(CorsConfiguration.ALL);
      configuration.setAllowCredentials(true);
      return configuration;
    }));

    // Resource server with two bearer shapes on one Authorization header
    // (docs/SPEC_M2M_AUTHORIZATION.md): "Bearer igrpm2m_…" goes to the opaque M2M introspector
    // (key resolved against our own store, principal m2m:<client>, MODULE:action authorities);
    // anything else takes the JWT path exactly as before. ROLE_ACTIVITI_USER is auto-granted to M2M
    // principals because the engine requires it — mirroring the JWT converter — and never the admin
    // role (M-16).
    final var jwtProvider = new JwtAuthenticationProvider(jwtDecoder);
    jwtProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
    final var m2mIntrospector = new M2mOpaqueTokenIntrospector(m2mKeyResolver,
        Set.of(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_USER));
    http.oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
        .authenticationManagerResolver(
            M2mAuthenticationManagers.m2mAware(new ProviderManager(jwtProvider), m2mIntrospector))
    );

    // Configure authorization rules and policy enforcement.
    // Business routes come from the authorization adapter, never from this class: see
    // docs/SPEC_ROUTE_AUTHORIZATION.md.
    http
        .authorizeHttpRequests((authorize) -> {

          // Error dispatches must stay reachable, otherwise denyAll() turns every error into a 403
          authorize.requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR).permitAll();

          authorize.requestMatchers(
              "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
              "/swagger-resources/**", "/webjars/**",
              "/actuator/health", "/actuator/health/**"
          ).permitAll();

          // M2M key management is security plumbing, not a business route: a dedicated gate, never
          // the catalogue (an IRN-provisionable M2MKEYS:* permission would let a non-super-admin, or
          // a key, mint keys). Requires a JWT super-admin: an M2M key is never a JwtAuthenticationToken
          // and no store can grant the super-admin role (SPEC_M2M_AUTHORIZATION.md M-12).
          authorize.requestMatchers("/m2m-keys/**").access(jwtSuperAdmin());


          routeAuthorization.getRules().forEach(rule -> {
            var matcher = rule.method() == null
                ? authorize.requestMatchers(rule.pattern())
                : authorize.requestMatchers(rule.method(), rule.pattern());
            final var permitted = withSuperAdmin(rule.anyAuthority());
            if (rule.pattern().startsWith(EMAIL_ACCESS_ROUTE)) {
              // The console is catalogued like any route (EMAIL_ACCESS_MAPPINGS:<action>, accept-also),
              // but the permission only counts on a request with an IRN session: with a session the
              // mapping is never consulted, so the authority came from System Administration. A mapped
              // token has no session and can never grant access here (SPEC_EMAIL_ACCESS_MAPPING E-6).
              matcher.access(consoleGate(permitted));
            } else {
              matcher.hasAnyAuthority(permitted);
            }
          });

          // No catalogue entry for the console (adapter=default, or a method outside the catalogue):
          // super admin only, never merely authenticated.
          authorize.requestMatchers(EMAIL_ACCESS_ROUTE + "/**").access(jwtSuperAdmin());

          if (routeAuthorization.denyUnmatched()) {
            authorize.anyRequest().denyAll();
          } else {
            authorize.anyRequest().authenticated();
          }
        })
        .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, _) -> {
          // DEBUG, not WARN: anonymous probes and expired tokens are routine noise
          LOGGER.debug("Unauthenticated request: {} {}", request.getMethod(), request.getRequestURI());
          response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Restricted Content\"");
          response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }));

    // Set session management to stateless (no session created for API requests)
    http.sessionManagement(t -> t.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // Disable CSRF
    http.csrf(AbstractHttpConfigurer::disable);

    http.addFilterBefore(iamUserProfileSyncFilter, AuthorizationFilter.class);

    return http.build();
  }

  /**
   * Publishes authorization decisions as application events. Spring Security only publishes denials
   * through this publisher, which {@link AuthorizationAuditListener} turns into structured audit logs.
   */
  @Bean
  public AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
    return new SpringAuthorizationEventPublisher(publisher);
  }

  /**
   * A Keycloak user JWT carrying the super-admin role. An M2M key is a BearerTokenAuthentication, never
   * this. The decision carries the required authority so the audit line names it.
   */
  private static AuthorizationManager<RequestAuthorizationContext> jwtSuperAdmin() {
      final var required = AuthorityUtils.createAuthorityList(ROLE_PREFIX + IgrpAuthorizationConstants.SUPER_ADMIN_ROLE);
      return (authenticationSupplier, context) -> {
          final var authentication = authenticationSupplier.get();
          return new AuthorityAuthorizationDecision(
                  authentication instanceof JwtAuthenticationToken && isSuperAdmin(authentication), required);
      };
  }

  /**
   * The console gate: a JWT that is the super admin, or a caller the adapter recognises as having a
   * session and who holds one of the catalogue authorities. Whether a request has a session is the
   * adapter's call (IAuthorizationServiceAdapter.hasSession), the same rule its permission path uses,
   * so gate and adapter can never disagree about a request; with a session the mapping is never
   * consulted, which is what keeps mapped tokens out of here. The decision always carries the
   * accepted authorities, so a denial logs them.
   */
  private AuthorizationManager<RequestAuthorizationContext> consoleGate(String[] permitted) {
      final var byAuthority = AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAnyAuthority(permitted);
      final var required = AuthorityUtils.createAuthorityList(permitted);
      return (authenticationSupplier, context) -> {
          final var authentication = authenticationSupplier.get();
          if (!(authentication instanceof JwtAuthenticationToken)) {
              return new AuthorityAuthorizationDecision(false, required);
          }
          if (!isSuperAdmin(authentication) && !authorizationService.hasSession(context.getRequest())) {
              return new AuthorityAuthorizationDecision(false, required);
          }
          // AuthorityAuthorizationManager answers with an AuthorityAuthorizationDecision (an AuthorizationDecision)
          return (AuthorizationDecision) byAuthority.authorize(authenticationSupplier, context);
      };
  }

  private static boolean isSuperAdmin(org.springframework.security.core.Authentication authentication) {
    final var superAdmin = ROLE_PREFIX + IgrpAuthorizationConstants.SUPER_ADMIN_ROLE;
    return authentication.getAuthorities().stream().anyMatch(a -> superAdmin.equals(a.getAuthority()));
  }

  /**
   * Adds the super admin role to a rule's accepted authorities, so the role does not have to be
   * repeated in every entry of the route table.
   */
  private static String[] withSuperAdmin(Set<String> authorities) {
    var accepted = new LinkedHashSet<>(authorities);
    accepted.add(ROLE_PREFIX + IgrpAuthorizationConstants.SUPER_ADMIN_ROLE);
    return accepted.toArray(String[]::new);
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {

    var converter = new JwtAuthenticationConverter();

    converter.setPrincipalClaimName(principalClaimName);

    converter.setJwtGrantedAuthoritiesConverter(jwt -> {

      final String sub = jwt.getSubject();

      HttpServletRequest request =
          ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder
              .getRequestAttributes()))
              .getRequest();

      Set<GrantedAuthority> authorities = new HashSet<>();
      final String token = jwt.getTokenValue();

      try {

        authorizationService
            .getActiveGroups(token, request)
            .forEach(r -> {
              String roleValue = !r.startsWith(ROLE_PREFIX) ? ROLE_PREFIX + r : r;
              String groupValue = !r.startsWith(ActivitiConstants.GROUP_PREFIX) ? ActivitiConstants.GROUP_PREFIX + r : r;
              authorities.add(new SimpleGrantedAuthority(roleValue));
              authorities.add(new SimpleGrantedAuthority(groupValue));
            });

        // the Jwt overload: without an IRN session the adapter grants what the application mapped to
        // the validated token's email claim (docs/SPEC_EMAIL_ACCESS_MAPPING.md)
        authorizationService
            .getPermissions(jwt, request)
            .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        // Activiti Admin or User role
        // the decoded token goes in, so the adapter reads claims without re-parsing or trusting a raw string
        if (authorizationService.isSuperAdmin(jwt, request)) {
          LOGGER.info("User [{}] granted super admin privileges", sub);
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + IgrpAuthorizationConstants.SUPER_ADMIN_ROLE));
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_ADMIN));
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_USER));
        } else {
          authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_USER));
        }

      } catch (Exception e) {
        // Fail closed: keep only the minimal Activiti role the engine needs, never an admin one, and
        // never any permission. Every permission-gated route will answer 403 until IRN recovers.
        LOGGER.error("SECURITY: failed to enrich authorities for [sub={}]; "
            + "granting the minimal role only, permission-gated routes will be denied", sub, e);
        authorities.clear();
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ActivitiConstants.ROLE_ACTIVITI_USER));
      }

      LOGGER.debug("Granted {} authorities", authorities.size());

      return authorities;

    });

    return converter;
  }

  @Bean
  public OAuth2AuthorizedClientProvider tokenExchange() {
    return new TokenExchangeOAuth2AuthorizedClientProvider();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return _ -> {
      throw new UsernameNotFoundException("UserDetailsService not used with JWT/Keycloak");
    };
  }

  @Bean
  public FilterRegistrationBean<IAMUserProfileSyncFilter> iamUserProfileSyncFilterRegistration(IAMUserProfileSyncFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }


}
