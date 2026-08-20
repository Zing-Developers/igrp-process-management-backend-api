package cv.igrp.platform.process.management.shared.security;

import cv.igrp.framework.process.runtime.auth.core.adapter.IAuthorizationServiceAdapter;
import cv.igrp.framework.process.runtime.auth.core.adapter.IRouteAuthorizationAdapter;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.cors.CorsConfiguration;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static cv.igrp.platform.process.management.shared.security.util.IgrpAuthorizationConstants.ROLE_PREFIX;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

  private final IAuthorizationServiceAdapter authorizationService;

  private final IRouteAuthorizationAdapter routeAuthorization;

  private final String principalClaimName;

  public SecurityConfig(IAuthorizationServiceAdapter authorizationService,
                        IRouteAuthorizationAdapter routeAuthorization,
                        @Value("${igrp.security.principal-claim-name}") String principalClaimName) {
    this.authorizationService = authorizationService;
    this.routeAuthorization = routeAuthorization;
    this.principalClaimName = principalClaimName;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, IAMUserProfileSyncFilter iamUserProfileSyncFilter) throws Exception {

    http.cors(cors -> cors.configurationSource(_ -> {
      var configuration = new CorsConfiguration();
      configuration.addAllowedOriginPattern(CorsConfiguration.ALL);
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

    // Configure OAuth2 Resource Server to use JWT tokens for authentication
    http.oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
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

          routeAuthorization.getRules().forEach(rule -> {
            var matcher = rule.method() == null
                ? authorize.requestMatchers(rule.pattern())
                : authorize.requestMatchers(rule.method(), rule.pattern());
            matcher.hasAnyAuthority(withSuperAdmin(rule.anyAuthority()));
          });

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

      final String email = jwt.getClaimAsString("email");
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

        authorizationService
            .getPermissions(token, request)
            .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        // Activiti Admin or User role
        if (authorizationService.isSuperAdmin(token, request)) {
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
