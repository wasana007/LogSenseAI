package com.logai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.service.OllamaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    private static final String DB_ANALYZE = "DB_ANALYZE";
    private static final String KAFKA_ANALYZE = "KAFKA_ANALYZE";
    private static final String OLLAMA_ANALYZE = "OLLAMA_ANALYZE";
    private static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";
    private static final String TOOL_RESULT = "result";
    private static final String LOG_MESSAGE = "some log";
    private static final String FALLBACK_RESULT = "fallback result";

    @Mock
    private ToolRouter toolRouter;

    @Mock
    private OllamaClient ollamaClient;

    @InjectMocks
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(toolRouter, ollamaClient, new ObjectMapper());
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @Test
        @DisplayName("delegates to toolRouter with resolved tool and original log message")
        void delegatesToToolRouter() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"" + DB_ANALYZE + "\"}");
            when(toolRouter.execute(DB_ANALYZE, LOG_MESSAGE)).thenReturn(TOOL_RESULT);

            String result = agentService.handle(LOG_MESSAGE);

            assertThat(result).isEqualTo(TOOL_RESULT);
            verify(toolRouter).execute(DB_ANALYZE, LOG_MESSAGE);
        }
    }

    @Nested
    @DisplayName("normalizeToolName — all three valid tools")
    class NormalizeToolName {

        @Test
        @DisplayName("routes DB_ANALYZE")
        void routesDbAnalyze() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"" + DB_ANALYZE + "\"}");
            when(toolRouter.execute(eq(DB_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(DB_ANALYZE, LOG_MESSAGE);
        }

        @Test
        @DisplayName("routes KAFKA_ANALYZE")
        void routesKafkaAnalyze() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"" + KAFKA_ANALYZE + "\"}");
            when(toolRouter.execute(eq(KAFKA_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(KAFKA_ANALYZE, LOG_MESSAGE);
        }

        @Test
        @DisplayName("routes OLLAMA_ANALYZE")
        void routesOllamaAnalyze() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"" + OLLAMA_ANALYZE + "\"}");
            when(toolRouter.execute(eq(OLLAMA_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(OLLAMA_ANALYZE, LOG_MESSAGE);
        }

        @Test
        @DisplayName("falls back to OLLAMA_ANALYZE on unknown tool")
        void fallbackOnUnknownTool() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"" + UNKNOWN_TOOL + "\"}");
            when(toolRouter.execute(eq(OLLAMA_ANALYZE), anyString())).thenReturn(FALLBACK_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(OLLAMA_ANALYZE, LOG_MESSAGE);
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        @DisplayName("normalizes lowercase tool name to uppercase")
        void normalizesLowercase() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"db_analyze\"}");
            when(toolRouter.execute(eq(DB_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(DB_ANALYZE, LOG_MESSAGE);
        }

        @Test
        @DisplayName("normalizes mixed-case tool name")
        void normalizesMixedCase() {
            when(ollamaClient.generate(anyString())).thenReturn("{\"tool\": \"Kafka_Analyze\"}");
            when(toolRouter.execute(eq(KAFKA_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(KAFKA_ANALYZE, LOG_MESSAGE);
        }

        @Test
        @DisplayName("extracts JSON embedded in surrounding text")
        void extractsJsonFromNoisyResponse() {
            when(ollamaClient.generate(anyString()))
                    .thenReturn("Sure! Here is my answer: {\"tool\": \"" + DB_ANALYZE + "\"} Hope that helps.");
            when(toolRouter.execute(eq(DB_ANALYZE), anyString())).thenReturn(TOOL_RESULT);

            agentService.handle(LOG_MESSAGE);

            verify(toolRouter).execute(DB_ANALYZE, LOG_MESSAGE);
        }
    }

    @Nested
    @DisplayName("Fallback to OLLAMA_ANALYZE")
    class Fallback {

        @ParameterizedTest(name = "falls back for input: [{0}]")
        @DisplayName("falls back to OLLAMA_ANALYZE for various invalid responses")
        @ValueSource(strings = {
                "{\"action\": \"" + DB_ANALYZE + "\"}",
                "{tool: " + DB_ANALYZE + "}",
                "I cannot determine the tool.",
                ""
        })
        void fallbackOnInvalidResponse(String response) {
            when(ollamaClient.generate(anyString())).thenReturn(response);
            when(toolRouter.execute(eq(OLLAMA_ANALYZE), anyString())).thenReturn(FALLBACK_RESULT);

            String result = agentService.handle(LOG_MESSAGE);

            assertThat(result).isEqualTo(FALLBACK_RESULT);
            verify(toolRouter).execute(OLLAMA_ANALYZE, LOG_MESSAGE);
        }
    }
}