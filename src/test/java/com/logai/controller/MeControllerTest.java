package com.logai.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private Authentication authentication;

    @InjectMocks
    private MeController meController;

    @Nested
    @DisplayName("me() — unauthenticated")
    class Unauthenticated {

        @Test
        @DisplayName("returns 401 when authentication is null")
        void returns401WhenAuthenticationIsNull() {
            ResponseEntity<?> response = meController.me(null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns authenticated=false when authentication is null")
        void returnsAuthenticatedFalseWhenNull() {
            ResponseEntity<?> response = meController.me(null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("authenticated", false);
        }

        @Test
        @DisplayName("returns error message when authentication is null")
        void returnsErrorMessageWhenNull() {
            ResponseEntity<?> response = meController.me(null);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "Unauthorized");
        }

        @Test
        @DisplayName("returns 401 when authentication is not authenticated")
        void returns401WhenNotAuthenticated() {
            when(authentication.isAuthenticated()).thenReturn(false);

            ResponseEntity<?> response = meController.me(authentication);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns authenticated=false when isAuthenticated() is false")
        void returnsAuthenticatedFalseWhenNotAuthenticated() {
            when(authentication.isAuthenticated()).thenReturn(false);

            ResponseEntity<?> response = meController.me(authentication);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("authenticated", false);
        }

        @Test
        @DisplayName("returns error message when isAuthenticated() is false")
        void returnsErrorMessageWhenNotAuthenticated() {
            when(authentication.isAuthenticated()).thenReturn(false);

            ResponseEntity<?> response = meController.me(authentication);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "Unauthorized");
        }
    }

    @Nested
    @DisplayName("me() — authenticated")
    class Authenticated {

        @Test
        @DisplayName("returns 200 when authenticated")
        void returns200WhenAuthenticated() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<?> response = meController.me(authentication);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("returns authenticated=true when authenticated")
        void returnsAuthenticatedTrue() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<?> response = meController.me(authentication);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("authenticated", true);
        }

        @Test
        @DisplayName("returns email from authentication name")
        void returnsEmailFromAuthenticationName() {
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<?> response = meController.me(authentication);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("email", EMAIL);
        }
    }
}