package com.logai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtUtil jwtUtil;
    private final JwtAuthFilter jwtAuthFilter;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public SecurityConfig(JwtUtil jwtUtil,
                          JwtAuthFilter jwtAuthFilter,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.jwtUtil = jwtUtil;
        this.jwtAuthFilter = jwtAuthFilter;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain filter(HttpSecurity http) throws Exception {
        http
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Cross-Origin-Opener-Policy", "same-origin-allow-popups"
                        ))
                )
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {
                })
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(this::configureAuthorization)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(this::handleUnauthorized))
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(
                                        new SelectAccountRequestResolver(clientRegistrationRepository)
                                )
                        )
                        .successHandler(this::handleOAuth2Success)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers(
                        "/oauth2/**",
                        "/login/**",
                        "/login-success",
                        "/error",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/ws/**",
                        "/ws/info/**",
                        "/api/v1/logs/search",
                        "/api/v1/logs/search/**",
                        "/api/v1/logs/*"
                ).permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated();
    }

    private void handleUnauthorized(HttpServletRequest request,
                                    HttpServletResponse response,
                                    org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"authenticated\":false,\"error\":\"Unauthorized\"}");
    }

    private void handleOAuth2Success(HttpServletRequest request,
                                     HttpServletResponse response,
                                     org.springframework.security.core.Authentication auth)
            throws IOException {
        OAuth2User oAuth2User = (OAuth2User) auth.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        String token = jwtUtil.generateToken(email, name);

        log.info("[OAuth2] Login success | email={}", email);

        response.sendRedirect(frontendUrl + "/login-success?token=" + token);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static class SelectAccountRequestResolver implements OAuth2AuthorizationRequestResolver {

        private final DefaultOAuth2AuthorizationRequestResolver delegate;

        SelectAccountRequestResolver(ClientRegistrationRepository repo) {
            this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                    repo, "/oauth2/authorization"
            );
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            return customize(delegate.resolve(request));
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request,
                                                  String clientRegistrationId) {
            return customize(delegate.resolve(request, clientRegistrationId));
        }

        private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req) {
            if (req == null) return null;

            Map<String, Object> extra = new LinkedHashMap<>(req.getAdditionalParameters());
            extra.put("prompt", "select_account");

            return OAuth2AuthorizationRequest.from(req)
                    .additionalParameters(extra)
                    .build();
        }
    }
}