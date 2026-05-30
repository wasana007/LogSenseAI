package com.logai.agent;

import com.logai.tools.DbTool;
import com.logai.tools.KafkaTool;
import com.logai.tools.OllamaTool;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRouterTest {

    private static final String DB_ANALYZE = "DB_ANALYZE";
    private static final String KAFKA_ANALYZE = "KAFKA_ANALYZE";
    private static final String OLLAMA_ANALYZE = "OLLAMA_ANALYZE";
    private static final String DB_RESULT = "db result";
    private static final String KAFKA_RESULT = "kafka result";
    private static final String OLLAMA_RESULT = "ollama result";
    private static final String TOOL_RESULT = "result";
    private static final String LOG_MESSAGE = "some log";

    @Mock
    private DbTool dbTool;

    @Mock
    private KafkaTool kafkaTool;

    @Mock
    private OllamaTool ollamaTool;

    @InjectMocks
    private ToolRouter toolRouter;

    @Nested
    @DisplayName("Known tools")
    class KnownTools {

        @Test
        @DisplayName("routes DB_ANALYZE to DbTool")
        void routesToDbTool() {
            when(dbTool.run(LOG_MESSAGE)).thenReturn(DB_RESULT);

            String result = toolRouter.execute(DB_ANALYZE, LOG_MESSAGE);

            assertThat(result).isEqualTo(DB_RESULT);
            verify(dbTool).run(LOG_MESSAGE);
            verifyNoInteractions(kafkaTool, ollamaTool);
        }

        @Test
        @DisplayName("routes KAFKA_ANALYZE to KafkaTool")
        void routesToKafkaTool() {
            when(kafkaTool.run(LOG_MESSAGE)).thenReturn(KAFKA_RESULT);

            String result = toolRouter.execute(KAFKA_ANALYZE, LOG_MESSAGE);

            assertThat(result).isEqualTo(KAFKA_RESULT);
            verify(kafkaTool).run(LOG_MESSAGE);
            verifyNoInteractions(dbTool, ollamaTool);
        }

        @Test
        @DisplayName("routes OLLAMA_ANALYZE to OllamaTool")
        void routesToOllamaTool() {
            when(ollamaTool.run(LOG_MESSAGE)).thenReturn(OLLAMA_RESULT);

            String result = toolRouter.execute(OLLAMA_ANALYZE, LOG_MESSAGE);

            assertThat(result).isEqualTo(OLLAMA_RESULT);
            verify(ollamaTool).run(LOG_MESSAGE);
            verifyNoInteractions(dbTool, kafkaTool);
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        @DisplayName("normalizes lowercase to uppercase")
        void normalizesLowercase() {
            when(dbTool.run(LOG_MESSAGE)).thenReturn(DB_RESULT);

            toolRouter.execute("db_analyze", LOG_MESSAGE);

            verify(dbTool).run(LOG_MESSAGE);
        }

        @Test
        @DisplayName("normalizes whitespace-padded decision")
        void normalizesWhitespace() {
            when(kafkaTool.run(LOG_MESSAGE)).thenReturn(KAFKA_RESULT);

            toolRouter.execute("  KAFKA_ANALYZE  ", LOG_MESSAGE);

            verify(kafkaTool).run(LOG_MESSAGE);
        }

        @Test
        @DisplayName("normalizes spaces to underscores")
        void normalizesSpacesToUnderscores() {
            when(dbTool.run(LOG_MESSAGE)).thenReturn(DB_RESULT);

            toolRouter.execute("DB ANALYZE", LOG_MESSAGE);

            verify(dbTool).run(LOG_MESSAGE);
        }
    }

    @Nested
    @DisplayName("Fallback to OllamaTool")
    class Fallback {

        @Test
        @DisplayName("falls back to OllamaTool when decision is null")
        void fallbackOnNull() {
            when(ollamaTool.run(LOG_MESSAGE)).thenReturn(OLLAMA_RESULT);

            String result = toolRouter.execute(null, LOG_MESSAGE);

            assertThat(result).isEqualTo(OLLAMA_RESULT);
            verify(ollamaTool).run(LOG_MESSAGE);
            verifyNoInteractions(dbTool, kafkaTool);
        }

        @ParameterizedTest(name = "falls back for unknown decision: [{0}]")
        @DisplayName("falls back to OllamaTool for unknown decisions")
        @ValueSource(strings = {"UNKNOWN", "RANDOM_TOOL", "GPT_ANALYZE", ""})
        void fallbackOnUnknownDecision(String decision) {
            when(ollamaTool.run(LOG_MESSAGE)).thenReturn(OLLAMA_RESULT);

            String result = toolRouter.execute(decision, LOG_MESSAGE);

            assertThat(result).isEqualTo(OLLAMA_RESULT);
            verify(ollamaTool).run(LOG_MESSAGE);
            verifyNoInteractions(dbTool, kafkaTool);
        }
    }

    @Nested
    @DisplayName("Log message forwarding")
    class LogMessageForwarding {

        @Test
        @DisplayName("forwards exact log message to DbTool")
        void forwardsLogMessageToDbTool() {
            String specificLog = "ERROR: connection timeout on port 5432";
            when(dbTool.run(specificLog)).thenReturn(TOOL_RESULT);

            toolRouter.execute(DB_ANALYZE, specificLog);

            verify(dbTool).run(specificLog);
        }

        @Test
        @DisplayName("forwards exact log message to KafkaTool")
        void forwardsLogMessageToKafkaTool() {
            String specificLog = "Kafka consumer lag detected on topic orders";
            when(kafkaTool.run(specificLog)).thenReturn(TOOL_RESULT);

            toolRouter.execute(KAFKA_ANALYZE, specificLog);

            verify(kafkaTool).run(specificLog);
        }
    }
}