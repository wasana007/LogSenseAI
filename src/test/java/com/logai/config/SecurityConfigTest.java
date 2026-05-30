package com.logai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SelectAccountRequestResolverTest {

    private static final String PROMPT_KEY = "prompt";
    private static final String PROMPT_VALUE = "select_account";

    private SecurityConfig.SelectAccountRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistrationRepository repo = mock(ClientRegistrationRepository.class);
        resolver = new SecurityConfig.SelectAccountRequestResolver(repo);
    }

    private OAuth2AuthorizationRequest buildRequest(Map<String, Object> additionalParameters) {
        return OAuth2AuthorizationRequest
                .authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope("email", "profile")
                .state("state-value")
                .additionalParameters(additionalParameters)
                .build();
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest request) {
        return (OAuth2AuthorizationRequest) ReflectionTestUtils
                .invokeMethod(resolver, "customize", request);
    }

    @Nested
    @DisplayName("customize()")
    class Customize {

        @Test
        @DisplayName("returns null when delegate returns null")
        void returnsNullWhenDelegateReturnsNull() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some/path");

            OAuth2AuthorizationRequest result = resolver.resolve(request);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("adds prompt=select_account to additional parameters")
        void addsSelectAccountPrompt() {
            OAuth2AuthorizationRequest original = buildRequest(Map.of());

            OAuth2AuthorizationRequest customized = customize(original);

            assertThat(customized).isNotNull();
            assertThat(customized.getAdditionalParameters())
                    .containsEntry(PROMPT_KEY, PROMPT_VALUE);
        }

        @Test
        @DisplayName("preserves existing additional parameters")
        void preservesExistingParameters() {
            OAuth2AuthorizationRequest original = buildRequest(
                    Map.of("existing_param", "existing_value")
            );

            OAuth2AuthorizationRequest result = customize(original);

            assertThat(result.getAdditionalParameters())
                    .containsEntry(PROMPT_KEY, PROMPT_VALUE)
                    .containsEntry("existing_param", "existing_value");
        }
    }
}