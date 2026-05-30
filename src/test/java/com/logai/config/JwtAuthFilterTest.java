package com.logai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String EMAIL = "user@example.com";
    private static final String BEARER_VALID = "Bearer " + VALID_TOKEN;
    private static final String BEARER_INVALID = "Bearer " + INVALID_TOKEN;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Valid token")
    class ValidToken {

        @Test
        @DisplayName("sets authentication in SecurityContext when token is valid")
        void setsAuthenticationWhenTokenValid() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(BEARER_VALID);
            when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(true);
            when(jwtUtil.extractEmail(VALID_TOKEN)).thenReturn(EMAIL);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(EMAIL);
            assertThat(auth.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("continues filter chain after setting authentication")
        void continuesFilterChainAfterAuth() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(BEARER_VALID);
            when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(true);
            when(jwtUtil.extractEmail(VALID_TOKEN)).thenReturn(EMAIL);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Invalid token")
    class InvalidToken {

        @Test
        @DisplayName("does not set authentication when token is invalid")
        void doesNotSetAuthWhenTokenInvalid() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(BEARER_INVALID);
            when(jwtUtil.isValid(INVALID_TOKEN)).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("does not extract email when token is invalid")
        void doesNotExtractEmailWhenTokenInvalid() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(BEARER_INVALID);
            when(jwtUtil.isValid(INVALID_TOKEN)).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(jwtUtil, never()).extractEmail(any());
        }

        @Test
        @DisplayName("continues filter chain even when token is invalid")
        void continuesFilterChainAfterInvalidToken() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(BEARER_INVALID);
            when(jwtUtil.isValid(INVALID_TOKEN)).thenReturn(false);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Missing or malformed Authorization header")
    class MissingOrMalformedHeader {

        @Test
        @DisplayName("does not set authentication when Authorization header is missing")
        void doesNotSetAuthWhenHeaderMissing() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(jwtUtil);
        }

        @Test
        @DisplayName("does not set authentication when header does not start with Bearer")
        void doesNotSetAuthWhenNotBearer() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verifyNoInteractions(jwtUtil);
        }

        @Test
        @DisplayName("continues filter chain when Authorization header is missing")
        void continuesFilterChainWhenHeaderMissing() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("continues filter chain when header is malformed")
        void continuesFilterChainWhenHeaderMalformed() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(jwtUtil);
        }
    }
}