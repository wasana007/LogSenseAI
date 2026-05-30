package com.logai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaClientTest {

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL = "llama3";
    private static final String PROMPT = "Analyze this log";
    private static final String GENERATE_URL = BASE_URL + "/api/generate";

    @Mock
    private RestTemplate restTemplate;

    private OllamaClient ollamaClient;

    @BeforeEach
    void setUp() {
        ollamaClient = new OllamaClient();
        ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(ollamaClient, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(ollamaClient, "model", MODEL);
    }

    @Nested
    @DisplayName("generate() — happy path")
    class HappyPath {

        @Test
        @DisplayName("returns response string from Ollama")
        void returnsResponseString() {
            when(restTemplate.postForObject(eq(GENERATE_URL), any(), eq(Map.class)))
                    .thenReturn(Map.of("response", "DB issue detected"));

            String result = ollamaClient.generate(PROMPT);

            assertThat(result).isEqualTo("DB issue detected");
        }

        @Test
        @DisplayName("returns response with extra fields in response map")
        void returnsResponseWithExtraFields() {
            when(restTemplate.postForObject(eq(GENERATE_URL), any(), eq(Map.class)))
                    .thenReturn(Map.of(
                            "response", "Kafka lag detected",
                            "model", MODEL,
                            "done", true
                    ));

            String result = ollamaClient.generate(PROMPT);

            assertThat(result).isEqualTo("Kafka lag detected");
        }
    }

    @Nested
    @DisplayName("generate() — null or empty response")
    class NullOrEmptyResponse {

        @Test
        @DisplayName("throws when Ollama returns null")
        void throwsWhenResponseIsNull() {
            when(restTemplate.postForObject(eq(GENERATE_URL), any(), eq(Map.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> ollamaClient.generate(PROMPT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tomt svar fra Ollama");
        }

        @Test
        @DisplayName("throws when response map is missing 'response' key")
        void throwsWhenResponseKeyMissing() {
            when(restTemplate.postForObject(eq(GENERATE_URL), any(), eq(Map.class)))
                    .thenReturn(Map.of("model", MODEL, "done", true));

            assertThatThrownBy(() -> ollamaClient.generate(PROMPT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tomt svar fra Ollama");
        }
    }

    @Nested
    @DisplayName("generate() — RestTemplate failure")
    class RestTemplateFailure {

        @Test
        @DisplayName("propagates exception when RestTemplate throws")
        void propagatesRestTemplateException() {
            when(restTemplate.postForObject(eq(GENERATE_URL), any(), eq(Map.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> ollamaClient.generate(PROMPT))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Connection refused");
        }
    }
}